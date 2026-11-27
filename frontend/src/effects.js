const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches

export function splitText(el) {
  if (!el || el.dataset.splitDone) return
  const text = el.textContent.trim()
  el.setAttribute('aria-label', text)
  el.setAttribute('data-split', '')
  el.textContent = ''
  let i = 0
  text.split(/\s+/).forEach((word, w, all) => {
    const wrap = document.createElement('span')
    wrap.className = 'word'
    wrap.setAttribute('aria-hidden', 'true')
    for (const ch of word) {
      const s = document.createElement('span')
      s.className = 'char'
      s.style.setProperty('--i', i++)
      s.textContent = ch
      wrap.appendChild(s)
    }
    el.appendChild(wrap)
    if (w < all.length - 1) el.appendChild(document.createTextNode(' '))
  })
  el.dataset.splitDone = '1'
}

const io = new IntersectionObserver((entries) => {
  entries.forEach((e) => {
    if (e.isIntersecting) {
      e.target.classList.add('is-in')
      io.unobserve(e.target)
    }
  })
}, { threshold: 0.15 })

export function reveal(root = document) {
  root.querySelectorAll('[data-split], .rise').forEach((el) => {
    if (el.classList.contains('is-in')) return
    if (reduce) el.classList.add('is-in')
    else io.observe(el)
  })
}

export function initToTop() {
  const toTop = document.querySelector('.to-top')
  if (!toTop) return
  addEventListener('scroll', () => toTop.classList.toggle('active', scrollY > 400), { passive: true })
  toTop.addEventListener('click', () => scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' }))
}

document.querySelectorAll('[data-split]').forEach(splitText)
reveal()
initToTop()