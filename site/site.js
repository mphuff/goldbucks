// Screenshot lightbox and the checksum copy button. The page works without this file.
(() => {
  const dialog = document.querySelector(".lightbox");
  const buttons = [...document.querySelectorAll(".gallery button")];
  let current = 0;

  if (dialog && typeof dialog.showModal === "function") {
    const image = dialog.querySelector("img");
    const caption = dialog.querySelector("figcaption");

    const show = (index) => {
      current = (index + buttons.length) % buttons.length;
      const button = buttons[current];
      image.src = button.dataset.full;
      image.alt = button.querySelector("img").alt;
      caption.textContent = button.dataset.caption;
    };

    buttons.forEach((button, index) => button.addEventListener("click", () => {
      show(index);
      dialog.showModal();
    }));
    dialog.querySelector(".lb-prev").addEventListener("click", () => show(current - 1));
    dialog.querySelector(".lb-next").addEventListener("click", () => show(current + 1));
    dialog.querySelector(".lb-close").addEventListener("click", () => dialog.close());
    dialog.addEventListener("click", (event) => {
      if (event.target === dialog) dialog.close();
    });
    dialog.addEventListener("keydown", (event) => {
      if (event.key === "ArrowLeft") show(current - 1);
      if (event.key === "ArrowRight") show(current + 1);
    });
  } else {
    // No <dialog> support: open the full-size picture instead.
    buttons.forEach((button) => button.addEventListener("click", () => {
      window.location.href = button.dataset.full;
    }));
  }

  document.querySelectorAll(".copy").forEach((button) => {
    button.addEventListener("click", async () => {
      const text = document.getElementById(button.dataset.copy).textContent.trim();
      try {
        await navigator.clipboard.writeText(text);
        button.textContent = "Copied";
      } catch {
        button.textContent = "Select and copy";
      }
      setTimeout(() => { button.textContent = "Copy"; }, 2000);
    });
  });
})();
