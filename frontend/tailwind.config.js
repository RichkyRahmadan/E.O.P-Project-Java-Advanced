/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}"
  ],
  theme: {
    extend: {
      colors: {
        primary: "#494fdf",
        "primary-bright": "#4f55f1",
        "primary-deep": "#3a40c4",
        "on-primary": "#ffffff",
        ink: "#191c1f",
        body: "#1f2226",
        charcoal: "#3a3d40",
        mute: "#505a63",
        ash: "#5c5e60",
        stone: "#8d969e",
        faint: "#c9c9cd",
        "on-dark": "#ffffff",
        "on-dark-mute": "rgba(255,255,255,0.72)",
        "canvas-light": "#ffffff",
        "canvas-dark": "#000000",
        "surface-soft": "#f4f4f4",
        "surface-card": "#ffffff",
        "surface-deep": "#0a0a0a",
        "surface-elevated": "#16181a",
        "hairline-light": "#e2e2e7",
        "hairline-dark": "rgba(255,255,255,0.12)",
        "hairline-strong": "#191c1f",
        "divider-soft": "rgba(255,255,255,0.06)",
        "accent-teal": "#00a87e",
        "accent-blue-link": "#376cd5",
        "accent-light-blue": "#007bc2",
        "accent-light-green": "#428619",
        "accent-green-text": "#006400",
        "accent-yellow": "#b09000",
        "accent-warning": "#ec7e00",
        "accent-pink": "#e61e49",
        "accent-danger": "#e23b4a",
        "accent-deep-red": "#8b0000",
        "accent-brown": "#936d62"
      },
      fontFamily: {
        display: ["Inter", "system-ui", "sans-serif"],
        body: ["Inter", "system-ui", "sans-serif"]
      },
      borderRadius: {
        sm: "8px",
        md: "12px",
        lg: "20px",
        xl: "28px"
      },
      spacing: {
        xxs: "4px",
        xs: "6px",
        sm: "8px",
        md: "14px",
        lg: "16px",
        xl: "24px",
        xxl: "32px",
        xxxl: "48px",
        block: "80px",
        section: "88px"
      }
    }
  },
  plugins: []
}
