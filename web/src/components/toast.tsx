import { useCallback, useMemo, useState, type ReactNode } from "react";
import { ToastContext, type ToastApi } from "./toast-context";

interface ToastMessage {
  id: number;
  text: string;
}

let nextId = 0;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [messages, setMessages] = useState<ToastMessage[]>([]);

  const push = useCallback((text: string) => {
    const id = nextId++;
    setMessages((current) => [...current, { id, text }]);
    setTimeout(() => setMessages((current) => current.filter((m) => m.id !== id)), 4000);
  }, []);

  const api = useMemo<ToastApi>(() => ({ push }), [push]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-stack" role="status" aria-live="polite">
        {messages.map((m) => (
          <div key={m.id} className="toast">
            {m.text}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
