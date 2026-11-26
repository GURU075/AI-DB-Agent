let toastHandler = null;

export function toast(type, title, message) {
  toastHandler?.({ type, title, message, id: Date.now() + Math.random() });
}

export function registerToastHandler(handler) {
  toastHandler = handler;
  return () => {
    if (toastHandler === handler) toastHandler = null;
  };
}

