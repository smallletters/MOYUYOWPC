module.exports = {
  root: true,
  // 第三方压缩/生成产物（如 Draco 解码器）不参与 lint，避免误报 no-constant-condition 等
  ignorePatterns: ['src/static/**'],
  env: {
    browser: true,
    es2021: true,
    node: true,
  },
  parser: 'vue-eslint-parser',
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
    requireConfigFile: false,
    parser: {
      js: '@babel/eslint-parser',
      ts: '@babel/eslint-parser',
      '<template>': '@babel/eslint-parser',
    },
    babelOptions: {
      parserOpts: {
        plugins: ['typescript', 'jsx'],
      },
    },
  },
  extends: ['eslint:recommended', 'plugin:vue/vue3-recommended'],
  plugins: ['vue'],
  rules: {
    'vue/multi-word-component-names': 'off',
    'vue/max-attributes-per-line': ['warn', { singleline: 3, multiline: 1 }],
    'vue/singleline-html-element-content-newline': 'off',
    'vue/html-closing-bracket-newline': 'off',
    'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    'no-console': process.env.NODE_ENV === 'production' ? 'warn' : 'off',
    'no-debugger': process.env.NODE_ENV === 'production' ? 'warn' : 'off',
  },
  globals: {
    uni: 'readonly',
    plus: 'readonly',
    wx: 'readonly',
    getApp: 'readonly',
    getCurrentPages: 'readonly',
  },
}
