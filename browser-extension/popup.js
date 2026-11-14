const el = id => document.getElementById(id);
let captured = null;
function readMenu() {
  const options = [];
  for (const node of document.querySelectorAll('a[href],button,[role="link"],[role="button"],[role="menuitem"]')) {
    const rect = node.getBoundingClientRect();
    const { visibility, opacity } = getComputedStyle(node);
    const left = Math.max(0, rect.left), top = Math.max(0, rect.top);
    const right = Math.min(innerWidth, rect.right), bottom = Math.min(innerHeight, rect.bottom);
    if (right <= left || bottom <= top || visibility !== "visible" || opacity === "0") continue;
    const hit = document.elementFromPoint((left + right) / 2, (top + bottom) / 2);
    if (!hit || !node.contains(hit) || node.matches(":disabled")) continue;
    const label = (node.innerText || node.getAttribute("aria-label") || node.querySelector("img[alt]")?.alt || node.title || "").trim();
    options.push({ id: String(options.length), label: label || "(sem rótulo)", x: left, y: top, width: right-left, height: bottom-top });
  }
  return { viewport: { width: innerWidth, height: innerHeight }, options };
}
function selected() {
  return captured?.options.filter(o => document.getElementById("option-" + o.id)?.checked) || [];
}
function refresh() {
  el("save").disabled = !el("task").value.trim() || !selected().length;
}
el("task").oninput = refresh;
el("options").onchange = refresh;
el("capture").onclick = async () => {
  captured = null; el("options").replaceChildren(); refresh(); el("capture").disabled = true;
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    const [frame] = await chrome.scripting.executeScript({ target: { tabId: tab.id }, func: readMenu });
    captured = frame.result;
    for (const option of captured.options) {
      const label = document.createElement("label"), box = document.createElement("input");
      box.type = "checkbox"; box.checked = true; box.id = "option-" + option.id;
      label.append(box, document.createTextNode(" " + option.label)); el("options").append(label);
    }
    el("status").textContent = captured.options.length ? "Mantenha marcadas as opções que pertencem ao menu da tarefa." : "Nenhuma opção identificada. Abra o menu e tente novamente.";
  } catch {
    el("status").textContent = "A captura falhou. Use a extensão em uma página web com o menu aberto.";
  } finally { el("capture").disabled = false; refresh(); }
};
el("save").onclick = () => {
  const data = { schema: "ai-cogwalk-menu-v1", task: el("task").value.trim(), viewport: captured.viewport, options: selected() };
  const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }));
  const link = document.createElement("a"); link.href = url; link.download = "menu-ai-cogwalk.json"; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000); el("status").textContent = "Captura exportada em JSON.";
};
