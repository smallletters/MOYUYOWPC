(function() {
  const result = {
    termsCheck: null,
    checkbox: null,
    ctaBtn: null
  };

  // 查找 terms-check 元素
  const termsCheckEls = document.querySelectorAll('[class*="terms-check"]');
  if (termsCheckEls.length > 0) {
    const el = termsCheckEls[0];
    result.termsCheck = {
      tagName: el.tagName,
      className: el.className,
      outerHTML: el.outerHTML
    };
  } else {
    result.termsCheck = 'NOT FOUND';
  }

  // 查找 checkbox 元素
  const checkboxEls = document.querySelectorAll('[class*="checkbox"]');
  if (checkboxEls.length > 0) {
    result.checkbox = Array.from(checkboxEls).map(function (el) {
      return {
        tagName: el.tagName,
        className: el.className,
        outerHTML: el.outerHTML
      };
    });
  } else {
    result.checkbox = 'NOT FOUND';
  }

  // 查找 cta-btn 元素
  const ctaBtnEls = document.querySelectorAll('[class*="cta-btn"]');
  if (ctaBtnEls.length > 0) {
    result.ctaBtn = Array.from(ctaBtnEls).map(function (el) {
      return {
        tagName: el.tagName,
        className: el.className,
        disabled: el.disabled || (typeof el.className === 'string' && el.className.indexOf('disabled') !== -1),
        outerHTML: el.outerHTML
      };
    });
  } else {
    result.ctaBtn = 'NOT FOUND';
  }

  return JSON.stringify(result, null, 2);
})();
