(function() {
  var el = document.querySelector('[class*="terms-check"]');
  if (!el) {
    return 'TERMS_CHECK_NOT_FOUND';
  }
  el.click();
  return 'clicked';
})();
