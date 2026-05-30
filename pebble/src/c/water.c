#include <pebble.h>

// Persistent-storage keys (local cache so the ring shows instantly on launch,
// before the phone replies to our RequestSync).
#define PERSIST_TODAY_OZ 1
#define PERSIST_GOAL_OZ  2

#define DEFAULT_GOAL_OZ 64  // matches WT_GOAL_DEFAULT on the Android side
#define GLASS_OZ        8   // matches WaterRepository.INCREMENT

static Window *s_window;
static Layer *s_ring_layer;
static TextLayer *s_amount_layer;
static TextLayer *s_hint_layer;

static int s_today_oz;
static int s_goal_oz;

static char s_amount_buf[32];

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------

static void update_amount_text(void) {
  snprintf(s_amount_buf, sizeof(s_amount_buf), "%d / %d oz", s_today_oz, s_goal_oz);
  text_layer_set_text(s_amount_layer, s_amount_buf);
}

static void ring_update_proc(Layer *layer, GContext *ctx) {
  GRect bounds = layer_get_bounds(layer);
  int32_t thickness = 12;

  // Draw into a centered square so the ring is a true circle. The screen is
  // taller than it is wide, so using the full bounds would stretch it into an oval.
  int16_t side = (bounds.size.w < bounds.size.h ? bounds.size.w : bounds.size.h) - 8;
  GRect square = GRect((bounds.size.w - side) / 2, (bounds.size.h - side) / 2, side, side);

  // Fraction of the goal met, clamped to [0, 1].
  int goal = s_goal_oz > 0 ? s_goal_oz : DEFAULT_GOAL_OZ;
  int filled = s_today_oz > goal ? goal : (s_today_oz < 0 ? 0 : s_today_oz);
  int32_t end_angle = TRIG_MAX_ANGLE * filled / goal;

  // Background track.
  graphics_context_set_fill_color(ctx, PBL_IF_COLOR_ELSE(GColorDarkGray, GColorBlack));
  graphics_fill_radial(ctx, square, GOvalScaleModeFitCircle, thickness,
                       0, TRIG_MAX_ANGLE);

  // Progress arc, starting at 12 o'clock.
  graphics_context_set_fill_color(ctx, PBL_IF_COLOR_ELSE(GColorVividCerulean, GColorWhite));
  graphics_fill_radial(ctx, square, GOvalScaleModeFitCircle, thickness,
                       0, end_angle);
}

static void redraw(void) {
  update_amount_text();
  if (s_ring_layer) {
    layer_mark_dirty(s_ring_layer);
  }
}

// ---------------------------------------------------------------------------
// AppMessage
// ---------------------------------------------------------------------------

static void send_request_sync(void) {
  DictionaryIterator *out;
  if (app_message_outbox_begin(&out) == APP_MSG_OK) {
    dict_write_uint8(out, MESSAGE_KEY_RequestSync, 1);
    app_message_outbox_send();
  }
}

static void send_log(int oz) {
  DictionaryIterator *out;
  if (app_message_outbox_begin(&out) == APP_MSG_OK) {
    dict_write_int32(out, MESSAGE_KEY_LogOz, oz);
    app_message_outbox_send();
  }
}

static void inbox_received(DictionaryIterator *iter, void *context) {
  Tuple *today = dict_find(iter, MESSAGE_KEY_TodayOz);
  Tuple *goal = dict_find(iter, MESSAGE_KEY_GoalOz);

  if (today) {
    s_today_oz = today->value->int32;
    persist_write_int(PERSIST_TODAY_OZ, s_today_oz);
  }
  if (goal) {
    s_goal_oz = goal->value->int32;
    persist_write_int(PERSIST_GOAL_OZ, s_goal_oz);
  }
  if (today || goal) {
    redraw();
  }
}

static void inbox_dropped(AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_WARNING, "inbox dropped: %d", reason);
}

static void outbox_failed(DictionaryIterator *iter, AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_WARNING, "outbox failed: %d", reason);
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

static void adjust_intake(int delta) {
  // Nothing to remove when already at zero.
  if (delta < 0 && s_today_oz <= 0) {
    return;
  }
  // Optimistically update locally, then tell the phone. The phone is the
  // source of truth and will echo back the authoritative TodayOz.
  s_today_oz += delta;
  if (s_today_oz < 0) {
    s_today_oz = 0;
  }
  persist_write_int(PERSIST_TODAY_OZ, s_today_oz);
  redraw();
  send_log(delta);
}

static void up_click_handler(ClickRecognizerRef recognizer, void *context) {
  adjust_intake(GLASS_OZ);
}

static void down_click_handler(ClickRecognizerRef recognizer, void *context) {
  adjust_intake(-GLASS_OZ);
}

static void click_config_provider(void *context) {
  window_single_click_subscribe(BUTTON_ID_UP, up_click_handler);
  window_single_click_subscribe(BUTTON_ID_DOWN, down_click_handler);
}

// ---------------------------------------------------------------------------
// Window
// ---------------------------------------------------------------------------

static void window_load(Window *window) {
  Layer *root = window_get_root_layer(window);
  GRect bounds = layer_get_bounds(root);

  // Ring fills most of the screen.
  s_ring_layer = layer_create(bounds);
  layer_set_update_proc(s_ring_layer, ring_update_proc);
  layer_add_child(root, s_ring_layer);

  // Centered amount text ("32 / 64 oz").
  GRect amount_rect = GRect(0, bounds.size.h / 2 - 18, bounds.size.w, 28);
  s_amount_layer = text_layer_create(amount_rect);
  text_layer_set_background_color(s_amount_layer, GColorClear);
  text_layer_set_text_alignment(s_amount_layer, GTextAlignmentCenter);
  text_layer_set_font(s_amount_layer, fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD));
  layer_add_child(root, text_layer_get_layer(s_amount_layer));

  // Hint below.
  GRect hint_rect = GRect(0, bounds.size.h / 2 + 12, bounds.size.w, 20);
  s_hint_layer = text_layer_create(hint_rect);
  text_layer_set_background_color(s_hint_layer, GColorClear);
  text_layer_set_text_alignment(s_hint_layer, GTextAlignmentCenter);
  text_layer_set_font(s_hint_layer, fonts_get_system_font(FONT_KEY_GOTHIC_14));
  text_layer_set_text(s_hint_layer, "UP +8   DN -8");
  layer_add_child(root, text_layer_get_layer(s_hint_layer));

  redraw();
}

static void window_unload(Window *window) {
  text_layer_destroy(s_hint_layer);
  text_layer_destroy(s_amount_layer);
  layer_destroy(s_ring_layer);
}

// ---------------------------------------------------------------------------
// App lifecycle
// ---------------------------------------------------------------------------

static void init(void) {
  // Restore last-known values so the ring is correct before the phone replies.
  s_today_oz = persist_exists(PERSIST_TODAY_OZ) ? persist_read_int(PERSIST_TODAY_OZ) : 0;
  s_goal_oz = persist_exists(PERSIST_GOAL_OZ) ? persist_read_int(PERSIST_GOAL_OZ) : DEFAULT_GOAL_OZ;

  app_message_register_inbox_received(inbox_received);
  app_message_register_inbox_dropped(inbox_dropped);
  app_message_register_outbox_failed(outbox_failed);
  app_message_open(app_message_inbox_size_maximum(), app_message_outbox_size_maximum());

  s_window = window_create();
  window_set_click_config_provider(s_window, click_config_provider);
  window_set_window_handlers(s_window, (WindowHandlers){
    .load = window_load,
    .unload = window_unload,
  });
  window_stack_push(s_window, true);

  // Ask the phone for the authoritative current totals.
  send_request_sync();
}

static void deinit(void) {
  window_destroy(s_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
