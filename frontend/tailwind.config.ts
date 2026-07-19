import type { Config } from 'tailwindcss'

// ShopMate visual system — "The Sunlit Fridge Door".
// Committed marigold on bright light surfaces; warmth lives in the brand
// color, never in cream-tinted neutrals. All colors OKLCH.
export default {
  content: ['./index.html', './preview.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Brand ("<alpha-value>" keeps /NN opacity modifiers working)
        marigold: {
          DEFAULT: 'oklch(0.79 0.155 75 / <alpha-value>)', // primary fills: header, add bar, checks
          deep: 'oklch(0.68 0.145 68 / <alpha-value>)', // hover/pressed, borders on marigold
          tint: 'oklch(0.93 0.07 85 / <alpha-value>)', // selection & hover washes
          faint: 'oklch(0.96 0.04 87 / <alpha-value>)', // chip backgrounds
        },
        // Text on marigold surfaces (darker shade of the brand hue, not gray)
        honey: {
          deep: 'oklch(0.40 0.08 68 / <alpha-value>)',
        },
        // Neutrals
        ground: 'oklch(0.98 0.005 85 / <alpha-value>)', // app background
        panel: 'oklch(1 0 0 / <alpha-value>)', // cards, the list "pad"
        line: 'oklch(0.90 0.015 80 / <alpha-value>)', // hairline borders / pad rules
        ink: {
          DEFAULT: 'oklch(0.27 0.025 65 / <alpha-value>)', // primary text, ≥12:1 on panel
          soft: 'oklch(0.47 0.03 65 / <alpha-value>)', // secondary text, ≥6:1
          mute: 'oklch(0.55 0.035 70 / <alpha-value>)', // placeholders / disabled, ≥4.5:1
        },
        danger: {
          DEFAULT: 'oklch(0.55 0.19 27 / <alpha-value>)',
          tint: 'oklch(0.96 0.025 27 / <alpha-value>)',
        },
      },
      fontFamily: {
        sans: [
          'Nunito Sans Variable',
          'Nunito Sans',
          'Seravek',
          'Avenir Next',
          'system-ui',
          'sans-serif',
        ],
      },
      fontSize: {
        // Product-register fixed rem scale, ~1.125 ratio
        label: ['0.8125rem', { lineHeight: '1.25rem' }], // 13px — meta, chips
        body: ['0.9375rem', { lineHeight: '1.375rem' }], // 15px — controls, prose
        item: ['1.0625rem', { lineHeight: '1.5rem' }], // 17px — list items, glanceable
        title: ['1.1875rem', { lineHeight: '1.625rem' }], // 19px — screen headers
        display: ['1.875rem', { lineHeight: '2.25rem' }], // 30px — login wordmark
      },
      zIndex: {
        header: '10',
        addbar: '20',
        overlay: '40',
        sheet: '50',
      },
      minHeight: {
        touch: '2.75rem', // 44px touch target floor
      },
      minWidth: {
        touch: '2.75rem',
      },
    },
  },
  plugins: [],
} satisfies Config
