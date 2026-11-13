/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#eef4fb', 100: '#d6e3f3', 200: '#adc6e6', 300: '#7ea3d3', 400: '#4f7fbd',
          500: '#2f62a3', 600: '#244e86', 700: '#1e3f6c', 800: '#18324f', 900: '#11243a',
        },
        accent: {
          50: '#effaf7', 100: '#d3f1e9', 200: '#a7e2d3', 300: '#6fcab6', 400: '#3fae97',
          500: '#22917c', 600: '#187563', 700: '#155e51', 800: '#134b42', 900: '#0f3d36',
        },
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      boxShadow: { card: '0 1px 2px rgba(17,36,58,0.06), 0 1px 3px rgba(17,36,58,0.08)' },
    },
  },
  plugins: [],
};
