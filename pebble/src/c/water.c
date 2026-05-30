#include <pebble.h>

// ---------------------------------------------------------------------------
// Water — Pebble watchapp with an offline-tolerant sync layer.
//
// The watch keeps a small persistent queue of pending "add a glass" ops. Each
// op has a unique, monotonic seq and the timestamp it was logged. The displayed
// total updates optimistically; ops are flushed to the phone one at a time and
// removed from the queue only once the phone ACKs them. This means logs made
// while the phone is locked / out of range are never lost and the ring never
// reverts — it just syncs when the phone becomes reachable again.
// ---------------------------------------------------------------------------

#define DEFAULT_GOAL_OZ 64  // matches WT_GOAL_DEFAULT on Android
#define GLASS_OZ        8   // matches WaterRepository.INCREMENT
#define MAX_PENDING     16  // queue cap (16 * 12B = 192B, under the 256B persist limit)
#define RETRY_MS        10000

// Persistent-storage keys.
#define PK_LOCAL_TOTAL 1
#define PK_GOAL        2
#define PK_NEXT_SEQ    3
#define PK_QUEUE       4  // blob: s_queue_len PendingAdd records
#define PK_QUEUE_LEN   5

typedef struct {
  uint32_t seq;
  int32_t amount;
  uint32_t ts;  // unix seconds, when logged on the watch
} PendingAdd;

static Window *s_window;
static Layer *s_ring_layer;
static TextLayer *s_amount_layer;
static TextLayer *s_hint_layer;
static TextLayer *s_sync_layer;

static int s_local_total;   // optimistic display total
static int s_goal_oz;
static uint32_t s_next_seq;

static PendingAdd s_queue[MAX_PENDING];
static int s_queue_len;

static bool s_inflight;          // a queue op is currently awaiting ACK
static uint32_t s_inflight_seq;

static AppTimer *s_retry_timer;
static char s_amount_buf[32];
static char s_sync_buf[24];

// ---------------------------------------------------------------------------
// Persistence
// ---------------------------------------------------------------------------

static void save_queue(void) {
  persist_write_int(PK_QUEUE_LEN, s_queue_len);
  if (s_queue_len > 0) {
    persist_write_data(PK_QUEUE, s_queue, s_queue_len * sizeof(PendingAdd));
  }
}

static void load_state(void) {
  s_local_total = persist_exists(PK_LOCAL_TOTAL) ? persist_read_int(PK_LOCAL_TOTAL) : 0;
  s_goal_oz = persist_exists(PK_GOAL) ? persist_read_int(PK_GOAL) : DEFAULT_GOAL_OZ;
  // Seed the seq from the clock on first run so it keeps increasing across
  // reinstalls (the phone dedupes by a high-water mark).
  s_next_seq = persist_exists(PK_NEXT_SEQ) ? (uint32_t)persist_read_int(PK_NEXT_SEQ)
                                           : (uint32_t)time(NULL);
  persist_write_int(PK_NEXT_SEQ, (int)s_next_seq);

  s_queue_len = persist_exists(PK_QUEUE_LEN) ? persist_read_int(PK_QUEUE_LEN) : 0;
  if (s_queue_len > MAX_PENDING) s_queue_len = MAX_PENDING;
  if (s_queue_len > 0) {
    persist_read_data(PK_QUEUE, s_queue, s_queue_len * sizeof(PendingAdd));
  }
}

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------

static void redraw(void) {
  snprintf(s_amount_buf, sizeof(s_amount_buf), "%d / %d oz", s_local_total, s_goal_oz);
  text_layer_set_text(s_amount_layer, s_amount_buf);

  if (s_queue_len > 0) {
    snprintf(s_sync_buf, sizeof(s_sync_buf), "syncing %d", s_queue_len);
    text_layer_set_text(s_sync_layer, s_sync_buf);
  } else {
    text_layer_set_text(s_sync_layer, "");
  }

  if (s_ring_layer) layer_mark_dirty(s_ring_layer);
}

static void ring_update_proc(Layer *layer, GContext *ctx) {
  GRect bounds = layer_get_bounds(layer);
  int32_t thickness = 12;

  // Centered square so the ring is a true circle (the screen is taller than wide).
  int16_t side = (bounds.size.w < bounds.size.h ? bounds.size.w : bounds.size.h) - 8;
  GRect square = GRect((bounds.size.w - side) / 2, (bounds.size.h - side) / 2, side, side);

  int goal = s_goal_oz > 0 ? s_goal_oz : DEFAULT_GOAL_OZ;
  int filled = s_local_total > goal ? goal : (s_local_total < 0 ? 0 : s_local_total);
  int32_t end_angle = TRIG_MAX_ANGLE * filled / goal;

  graphics_context_set_fill_color(ctx, PBL_IF_COLOR_ELSE(GColorDarkGray, GColorBlack));
  graphics_fill_radial(ctx, square, GOvalScaleModeFitCircle, thickness, 0, TRIG_MAX_ANGLE);

  graphics_context_set_fill_color(ctx, PBL_IF_COLOR_ELSE(GColorVividCerulean, GColorWhite));
  graphics_fill_radial(ctx, square, GOvalScaleModeFitCircle, thickness, 0, end_angle);
}

// ---------------------------------------------------------------------------
// AppMessage — outbound
// ---------------------------------------------------------------------------

// Send the front queue op. Removed from the queue only when the phone ACKs it
// (outbox_sent). One in-flight message at a time.
static void try_flush(void) {
  if (s_inflight || s_queue_len == 0) return;

  DictionaryIterator *out;
  if (app_message_outbox_begin(&out) != APP_MSG_OK) return;

  PendingAdd *op = &s_queue[0];
  dict_write_int32(out, MESSAGE_KEY_LogOz, op->amount);
  dict_write_int32(out, MESSAGE_KEY_WatchSeq, (int32_t)op->seq);
  dict_write_int32(out, MESSAGE_KEY_LogTs, (int32_t)op->ts);

  if (app_message_outbox_send() == APP_MSG_OK) {
    s_inflight = true;
    s_inflight_seq = op->seq;
  }
}

static void send_request_sync(void) {
  if (s_inflight) return;
  DictionaryIterator *out;
  if (app_message_outbox_begin(&out) != APP_MSG_OK) return;
  dict_write_uint8(out, MESSAGE_KEY_RequestSync, 1);
  app_message_outbox_send();
}

static void send_remove_last(void) {
  if (s_inflight) return;
  DictionaryIterator *out;
  if (app_message_outbox_begin(&out) != APP_MSG_OK) return;
  dict_write_uint8(out, MESSAGE_KEY_RemoveLast, 1);
  dict_write_int32(out, MESSAGE_KEY_WatchSeq, (int32_t)(s_next_seq++));
  persist_write_int(PK_NEXT_SEQ, (int)s_next_seq);
  app_message_outbox_send();
}

static void outbox_sent(DictionaryIterator *iter, void *context) {
  // Only a queue op sets s_inflight; nothing else is sent while one is pending.
  if (s_inflight) {
    for (int i = 0; i < s_queue_len; i++) {
      if (s_queue[i].seq == s_inflight_seq) {
        for (int j = i + 1; j < s_queue_len; j++) s_queue[j - 1] = s_queue[j];
        s_queue_len--;
        break;
      }
    }
    s_inflight = false;
    save_queue();
    redraw();
  }
  try_flush();
}

static void outbox_failed(DictionaryIterator *iter, AppMessageResult reason, void *context) {
  s_inflight = false;  // retried on the next timer / message
}

// ---------------------------------------------------------------------------
// AppMessage — inbound
// ---------------------------------------------------------------------------

static void inbox_received(DictionaryIterator *iter, void *context) {
  Tuple *goal = dict_find(iter, MESSAGE_KEY_GoalOz);
  Tuple *today = dict_find(iter, MESSAGE_KEY_TodayOz);

  if (goal) {
    s_goal_oz = goal->value->int32;
    persist_write_int(PK_GOAL, s_goal_oz);
  }
  // Only adopt the phone's total when fully synced; otherwise an echo would
  // clobber optimistic logs that haven't reached the phone yet.
  if (today && s_queue_len == 0 && !s_inflight) {
    s_local_total = today->value->int32;
    persist_write_int(PK_LOCAL_TOTAL, s_local_total);
  }

  redraw();
  try_flush();  // connection is alive — good time to drain the queue
}

static void inbox_dropped(AppMessageResult reason, void *context) {}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

static void up_click_handler(ClickRecognizerRef recognizer, void *context) {
  if (s_queue_len >= MAX_PENDING) return;  // queue full (very unlikely)
  PendingAdd op = { .seq = s_next_seq++, .amount = GLASS_OZ, .ts = (uint32_t)time(NULL) };
  s_queue[s_queue_len++] = op;
  s_local_total += GLASS_OZ;
  persist_write_int(PK_NEXT_SEQ, (int)s_next_seq);
  persist_write_int(PK_LOCAL_TOTAL, s_local_total);
  save_queue();
  redraw();
  try_flush();
}

static void down_click_handler(ClickRecognizerRef recognizer, void *context) {
  if (s_queue_len > 0) {
    // Undo the most recent unsynced add locally — nothing to remove on the phone.
    s_local_total -= s_queue[s_queue_len - 1].amount;
    s_queue_len--;
    persist_write_int(PK_LOCAL_TOTAL, s_local_total);
    save_queue();
    redraw();
  } else {
    // Nothing pending: ask the phone to delete its most recent entry (online).
    send_remove_last();
  }
}

static void click_config_provider(void *context) {
  window_single_click_subscribe(BUTTON_ID_UP, up_click_handler);
  window_single_click_subscribe(BUTTON_ID_DOWN, down_click_handler);
}

// ---------------------------------------------------------------------------
// Retry timer (runs while the app is open)
// ---------------------------------------------------------------------------

static void retry_cb(void *data) {
  try_flush();
  s_retry_timer = app_timer_register(RETRY_MS, retry_cb, NULL);
}

// ---------------------------------------------------------------------------
// Window
// ---------------------------------------------------------------------------

static void window_load(Window *window) {
  Layer *root = window_get_root_layer(window);
  GRect bounds = layer_get_bounds(root);

  s_ring_layer = layer_create(bounds);
  layer_set_update_proc(s_ring_layer, ring_update_proc);
  layer_add_child(root, s_ring_layer);

  s_amount_layer = text_layer_create(GRect(0, bounds.size.h / 2 - 22, bounds.size.w, 28));
  text_layer_set_background_color(s_amount_layer, GColorClear);
  text_layer_set_text_alignment(s_amount_layer, GTextAlignmentCenter);
  text_layer_set_font(s_amount_layer, fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD));
  layer_add_child(root, text_layer_get_layer(s_amount_layer));

  s_sync_layer = text_layer_create(GRect(0, bounds.size.h / 2 + 6, bounds.size.w, 16));
  text_layer_set_background_color(s_sync_layer, GColorClear);
  text_layer_set_text_alignment(s_sync_layer, GTextAlignmentCenter);
  text_layer_set_font(s_sync_layer, fonts_get_system_font(FONT_KEY_GOTHIC_14));
  layer_add_child(root, text_layer_get_layer(s_sync_layer));

  s_hint_layer = text_layer_create(GRect(0, bounds.size.h / 2 + 24, bounds.size.w, 18));
  text_layer_set_background_color(s_hint_layer, GColorClear);
  text_layer_set_text_alignment(s_hint_layer, GTextAlignmentCenter);
  text_layer_set_font(s_hint_layer, fonts_get_system_font(FONT_KEY_GOTHIC_14));
  text_layer_set_text(s_hint_layer, "UP +8   DN undo");
  layer_add_child(root, text_layer_get_layer(s_hint_layer));

  redraw();
}

static void window_unload(Window *window) {
  text_layer_destroy(s_hint_layer);
  text_layer_destroy(s_sync_layer);
  text_layer_destroy(s_amount_layer);
  layer_destroy(s_ring_layer);
}

// ---------------------------------------------------------------------------
// App lifecycle
// ---------------------------------------------------------------------------

static void init(void) {
  load_state();

  app_message_register_inbox_received(inbox_received);
  app_message_register_inbox_dropped(inbox_dropped);
  app_message_register_outbox_sent(outbox_sent);
  app_message_register_outbox_failed(outbox_failed);
  app_message_open(app_message_inbox_size_maximum(), app_message_outbox_size_maximum());

  s_window = window_create();
  window_set_click_config_provider(s_window, click_config_provider);
  window_set_window_handlers(s_window, (WindowHandlers){
    .load = window_load,
    .unload = window_unload,
  });
  window_stack_push(s_window, true);

  // Drain anything queued from a previous session, then ask for the latest total.
  if (s_queue_len > 0) {
    try_flush();
  } else {
    send_request_sync();
  }
  s_retry_timer = app_timer_register(RETRY_MS, retry_cb, NULL);
}

static void deinit(void) {
  window_destroy(s_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
