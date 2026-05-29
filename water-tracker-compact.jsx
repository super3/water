import { useState } from 'react';

// Water Tracker Widget — Variant D ("Flush Goal Marker").
// A 192px circular tile with a 300° ring (7 o'clock → 5 o'clock, 60° bottom gap)
// and a flush "+" button pinned at the 5 o'clock track terminus.

const TOKENS = {
  widgetBg: '#0f1418',
  pageBg: '#1a1f2a',
  ink: '#e8eef5',
  inkDim: '#6a7280',
  track: '#2a3038',
  accent: '#7aa2ff',
};

// Ring geometry (168px stage).
const CENTER = 84;
const RADIUS = 78; // (168 - 12) / 2
const STROKE = 12;
const START_DEG = 210; // 7 o'clock
const SWEEP_DEG = 300; // ends at 150° (5 o'clock)

// Angle (0° = top, clockwise) → point on the ring.
const polar = (deg) => [
  CENTER + RADIUS * Math.cos(((deg - 90) * Math.PI) / 180),
  CENTER + RADIUS * Math.sin(((deg - 90) * Math.PI) / 180),
];

// SVG arc path from START_DEG sweeping `sweep` degrees clockwise.
const arcPath = (sweep) => {
  const [x0, y0] = polar(START_DEG);
  const [x1, y1] = polar(START_DEG + sweep);
  const largeArc = sweep > 180 ? 1 : 0;
  return `M ${x0} ${y0} A ${RADIUS} ${RADIUS} 0 ${largeArc} 1 ${x1} ${y1}`;
};

export default function WaterTracker() {
  const [ounces, setOunces] = useState(32);
  const goal = 64;
  const increment = 8;

  const progress = Math.min(ounces / goal, 1);
  const [trackEndX, trackEndY] = polar(START_DEG + SWEEP_DEG);

  const addWater = () => setOunces((prev) => Math.min(prev + increment, 999));

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        background: TOKENS.pageBg,
        fontFamily: "'Nunito', sans-serif",
      }}
    >
      {/* Tile */}
      <div
        style={{
          width: 192,
          height: 192,
          borderRadius: '50%',
          background: TOKENS.widgetBg,
          boxShadow: '0 20px 40px -15px rgba(0,0,0,0.55)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {/* Ring stage */}
        <div style={{ position: 'relative', width: 168, height: 168 }}>
          <svg
            width="168"
            height="168"
            viewBox="0 0 168 168"
            style={{ position: 'absolute', inset: 0 }}
          >
            {/* Track */}
            <path
              d={arcPath(SWEEP_DEG)}
              fill="none"
              stroke={TOKENS.track}
              strokeWidth={STROKE}
              strokeLinecap="round"
            />
            {/* Progress */}
            <path
              d={arcPath(progress * SWEEP_DEG)}
              fill="none"
              stroke={TOKENS.accent}
              strokeWidth={STROKE}
              strokeLinecap="round"
              style={{ transition: 'all 0.5s ease-out' }}
            />
          </svg>

          {/* Center text column */}
          <div
            style={{
              position: 'absolute',
              inset: 0,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 2,
            }}
          >
            {/* Metric icon (droplet) */}
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              style={{ marginBottom: 6 }}
            >
              <path
                d="M12 2.5c0 0 7 7.6 7 12.3a7 7 0 1 1-14 0C5 10.1 12 2.5 12 2.5z"
                fill={TOKENS.accent}
              />
            </svg>
            {/* Value + unit */}
            <div
              style={{
                color: TOKENS.ink,
                fontSize: 38,
                fontWeight: 700,
                letterSpacing: '-0.01em',
                lineHeight: 1,
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              {ounces}
              <span
                style={{
                  fontSize: 18,
                  fontWeight: 600,
                  color: TOKENS.inkDim,
                  marginLeft: 2,
                }}
              >
                oz
              </span>
            </div>
            {/* Goal line */}
            <div
              style={{
                fontSize: 16,
                fontWeight: 600,
                color: TOKENS.inkDim,
                marginTop: 4,
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              / {goal}oz
            </div>
          </div>

          {/* Flush "+" button at the 5 o'clock track terminus */}
          <button
            type="button"
            aria-label={`+${increment}oz`}
            onClick={addWater}
            style={{
              position: 'absolute',
              left: trackEndX,
              top: trackEndY,
              transform: 'translate(-50%, -50%)',
              width: 30,
              height: 30,
              borderRadius: '50%',
              border: 'none',
              padding: 0,
              background: TOKENS.accent,
              color: TOKENS.widgetBg,
              fontSize: 18,
              fontWeight: 800,
              lineHeight: 1,
              cursor: 'pointer',
            }}
          >
            +
          </button>
        </div>
      </div>
    </div>
  );
}
