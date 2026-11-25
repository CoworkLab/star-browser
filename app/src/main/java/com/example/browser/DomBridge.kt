package com.example.browser

import org.json.JSONObject

object DomBridge {

    val SNAPSHOT_INTERACTIVE_JS = """
        (function() {
            try {
                const prev = document.querySelectorAll('[data-star-ref]');
                prev.forEach(el => el.removeAttribute('data-star-ref'));

                const selector = 'a[href], button, input, textarea, select, [role="button"], [role="link"], [role="textbox"], [role="checkbox"], [role="radio"], [contenteditable="true"]';
                const nodes = Array.from(document.querySelectorAll(selector));
                const elements = [];
                let counter = 1;

                for (const el of nodes) {
                    if (counter > 80) break;
                    const rect = el.getBoundingClientRect();
                    const style = window.getComputedStyle(el);
                    if (style.display === 'none' || style.visibility === 'hidden' || rect.width < 4 || rect.height < 4) continue;
                    const ref = 'e' + counter++;
                    el.setAttribute('data-star-ref', ref);

                    const tag = el.tagName.toLowerCase();
                    const role = el.getAttribute('role') || (tag === 'a' ? 'link' : tag === 'button' ? 'button' : tag);
                    const text = (el.innerText || el.value || el.getAttribute('aria-label') || el.getAttribute('placeholder') || el.getAttribute('title') || '')
                        .replace(/\s+/g, ' ')
                        .trim()
                        .slice(0, 90);
                    const type = el.getAttribute('type') || '';
                    const href = tag === 'a' ? (el.getAttribute('href') || '').slice(0, 120) : '';
                    const checked = (el.type === 'checkbox' || el.type === 'radio') ? !!el.checked : null;

                    elements.push({
                        ref: '@' + ref,
                        role: role,
                        tag: tag,
                        type: type,
                        name: text,
                        href: href,
                        checked: checked
                    });
                }

                const bodyClone = document.body ? document.body.cloneNode(true) : null;
                if (bodyClone) {
                    bodyClone.querySelectorAll('script, style, noscript, svg').forEach(n => n.remove());
                }
                const pageText = (bodyClone ? bodyClone.innerText : '')
                    .replace(/\n{3,}/g, '\n\n')
                    .trim()
                    .slice(0, 4500);

                return JSON.stringify({
                    title: document.title || '',
                    url: window.location.href || '',
                    scrollY: Math.round(window.scrollY || 0),
                    scrollHeight: Math.round((document.documentElement && document.documentElement.scrollHeight) || 0),
                    elements: elements,
                    text: pageText
                });
            } catch (err) {
                return JSON.stringify({ error: String(err) });
            }
        })();
    """.trimIndent()

    val READER_MODE_JS = """
        (function() {
            try {
                const container = document.querySelector('article') || document.querySelector('main') || document.body;
                const clone = container ? container.cloneNode(true) : null;
                if (clone) {
                    clone.querySelectorAll('script, style, nav, footer, aside, noscript, iframe, form').forEach(n => n.remove());
                }
                const blocks = clone ? Array.from(clone.querySelectorAll('h1,h2,h3,p,li,blockquote'))
                    .map(n => (n.innerText || '').replace(/\s+/g, ' ').trim())
                    .filter(t => t.length > 20) : [];
                const bodyText = blocks.length > 0
                    ? blocks.join('\n\n')
                    : ((clone ? clone.innerText : '') || '').replace(/\n{3,}/g, '\n\n').trim();
                const words = bodyText.split(/\s+/).filter(Boolean).length;
                const metaAuthor = document.querySelector('meta[name="author"]');
                return JSON.stringify({
                    title: document.title || 'Article',
                    byline: metaAuthor ? metaAuthor.getAttribute('content') : window.location.hostname,
                    readMinutes: Math.max(1, Math.round(words / 220)),
                    content: bodyText.slice(0, 18000)
                });
            } catch (e) {
                return JSON.stringify({ error: String(e) });
            }
        })();
    """.trimIndent()

    fun normalizeRef(ref: String): String = ref.trim().removePrefix("@")

    fun clickRefJs(ref: String): String {
        val q = JSONObject.quote(normalizeRef(ref))
        return """
            (function() {
                const el = document.querySelector('[data-star-ref=' + $q + ']');
                if (!el) return JSON.stringify({ ok: false, error: 'Element ' + $q + ' not found. Run browser_snapshot first.' });
                el.scrollIntoView({ behavior: 'instant', block: 'center' });
                el.focus();
                el.click();
                return JSON.stringify({ ok: true, clicked: $q, text: (el.innerText || el.value || '').trim().slice(0, 80) });
            })();
        """.trimIndent()
    }

    fun fillRefJs(ref: String, value: String): String {
        val qRef = JSONObject.quote(normalizeRef(ref))
        val qVal = JSONObject.quote(value)
        return """
            (function() {
                const el = document.querySelector('[data-star-ref=' + $qRef + ']');
                if (!el) return JSON.stringify({ ok: false, error: 'Element ' + $qRef + ' not found.' });
                el.scrollIntoView({ behavior: 'instant', block: 'center' });
                el.focus();
                if (el.isContentEditable) {
                    el.innerText = $qVal;
                } else {
                    const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                    const desc = Object.getOwnPropertyDescriptor(proto, 'value');
                    if (desc && desc.set) desc.set.call(el, $qVal);
                    else el.value = $qVal;
                }
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return JSON.stringify({ ok: true, filled: $qRef });
            })();
        """.trimIndent()
    }

    fun typeRefJs(ref: String, value: String): String {
        val qRef = JSONObject.quote(normalizeRef(ref))
        val qVal = JSONObject.quote(value)
        return """
            (function() {
                const el = document.querySelector('[data-star-ref=' + $qRef + ']');
                if (!el) return JSON.stringify({ ok: false, error: 'Element ' + $qRef + ' not found.' });
                el.scrollIntoView({ behavior: 'instant', block: 'center' });
                el.focus();
                const current = el.isContentEditable ? (el.innerText || '') : (el.value || '');
                const next = current + $qVal;
                if (el.isContentEditable) el.innerText = next;
                else el.value = next;
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return JSON.stringify({ ok: true, typed: $qRef });
            })();
        """.trimIndent()
    }

    fun pressKeyJs(key: String): String {
        val qKey = JSONObject.quote(key)
        return """
            (function() {
                const target = document.activeElement || document.body;
                target.dispatchEvent(new KeyboardEvent('keydown', { key: $qKey, bubbles: true }));
                target.dispatchEvent(new KeyboardEvent('keyup', { key: $qKey, bubbles: true }));
                if ($qKey === 'Enter' && target.form) {
                    if (target.form.requestSubmit) target.form.requestSubmit();
                    else target.form.submit();
                }
                return JSON.stringify({ ok: true, pressed: $qKey });
            })();
        """.trimIndent()
    }

    fun selectRefJs(ref: String, value: String): String {
        val qRef = JSONObject.quote(normalizeRef(ref))
        val qVal = JSONObject.quote(value)
        return """
            (function() {
                const el = document.querySelector('[data-star-ref=' + $qRef + ']');
                if (!el || el.tagName !== 'SELECT') return JSON.stringify({ ok: false, error: 'Select element ' + $qRef + ' not found.' });
                const match = Array.from(el.options).find(o => o.value === $qVal || o.text.trim() === $qVal);
                if (!match) return JSON.stringify({ ok: false, error: 'Option not found in select ' + $qRef });
                el.value = match.value;
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return JSON.stringify({ ok: true, selected: match.value });
            })();
        """.trimIndent()
    }

    fun checkRefJs(ref: String, checked: Boolean): String {
        val qRef = JSONObject.quote(normalizeRef(ref))
        return """
            (function() {
                const el = document.querySelector('[data-star-ref=' + $qRef + ']');
                if (!el) return JSON.stringify({ ok: false, error: 'Element ' + $qRef + ' not found.' });
                if (el.checked !== $checked) el.click();
                return JSON.stringify({ ok: true, ref: $qRef, checked: !!el.checked });
            })();
        """.trimIndent()
    }

    fun scrollJs(direction: String, pixels: Int): String {
        val deltaY = when (direction.lowercase()) {
            "up" -> -pixels
            "down" -> pixels
            else -> 0
        }
        val deltaX = when (direction.lowercase()) {
            "left" -> -pixels
            "right" -> pixels
            else -> 0
        }
        return """
            (function() {
                window.scrollBy({ left: $deltaX, top: $deltaY, behavior: 'smooth' });
                return JSON.stringify({ ok: true, scrollY: Math.round(window.scrollY || 0) });
            })();
        """.trimIndent()
    }

    fun scrollIntoViewJs(ref: String): String {
        val qRef = JSONObject.quote(normalizeRef(ref))
        return """
            (function() {
                const el = document.querySelector('[data-star-ref=' + $qRef + ']');
                if (!el) return JSON.stringify({ ok: false, error: 'Element ' + $qRef + ' not found.' });
                el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                return JSON.stringify({ ok: true, scrolledTo: $qRef });
            })();
        """.trimIndent()
    }
}
