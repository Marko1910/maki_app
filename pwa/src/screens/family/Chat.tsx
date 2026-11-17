import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Empty, Icon, RumiAvatar, useToast } from "../../components/ui";
import { askAssistant, createConversation, humanize, loadChatMessages, loadConversations, saveChatMessage, type Row } from "../../lib/api";
import { ago } from "../../lib/util";
import { goBack } from "../../lib/flow";

type Turn = { role: "user" | "assistant"; content: string };
const MAX_TURNS = 12;

export function Chat() {
  const nav = useNavigate();
  const toast = useToast();
  const [turns, setTurns] = useState<Turn[]>([]);
  const [text, setText] = useState("");
  const [typing, setTyping] = useState(false);
  const [conversation, setConversation] = useState<string | null>(null);
  const [list, setList] = useState<Row[] | null>(null);
  const end = useRef<HTMLDivElement>(null);
  useEffect(() => { end.current?.scrollIntoView({ behavior: "smooth" }); }, [turns, typing]);

  const send = async (raw: string) => {
    const content = raw.trim();
    if (!content || typing) return;
    setText("");
    const next: Turn[] = [...turns, { role: "user", content }];
    setTurns(next);
    setTyping(true);
    try {
      // History is a nicety: a failed save must not lose the answer.
      let id = conversation;
      if (!id) { id = await createConversation(content.slice(0, 60)).catch(() => null); setConversation(id); }
      if (id) await saveChatMessage(id, "user", content).catch(() => undefined);
      const reply = await askAssistant(next.slice(-MAX_TURNS));
      setTurns([...next, { role: "assistant", content: reply }]);
      if (id) await saveChatMessage(id, "assistant", reply).catch(() => undefined);
    } catch (e) {
      toast(humanize(e));
    } finally { setTyping(false); }
  };
  const openList = async () => {
    try { setList(await loadConversations()); } catch (e) { toast(humanize(e)); }
  };
  const open = async (id: string) => {
    try {
      const msgs = await loadChatMessages(id);
      setConversation(id);
      setTurns([...msgs.map((m) => ({ role: m.role, content: m.content }))]);
      setList(null);
    } catch { toast("No se pudo cargar la conversación."); }
  };

  return (
    <main className="screen" style={{ paddingBottom: "calc(var(--safe-b) + 150px)" }}>
      <header className="glass row" style={{ position: "sticky", top: "calc(var(--safe-t) + 8px)", zIndex: 5, padding: 8 }}>
        <button className="icon-btn" aria-label="Volver" onClick={() => (list ? setList(null) : goBack(nav))}><Icon name="back" /></button>
        <RumiAvatar size={36} />
        <h1 className="t-card min0">{list ? "Conversaciones" : "Rumi"}</h1>
        {!list && <button className="chip" style={{ minHeight: 48 }} onClick={() => void openList()}><Icon name="chat" size={16} /> Conversaciones</button>}
        {list && <button className="chip" style={{ minHeight: 48 }} onClick={() => { setTurns([]); setConversation(null); setList(null); toast("Nueva conversación lista."); }}><Icon name="plus" size={16} /> Nueva</button>}
      </header>

      {list ? (list.length ? <div className="glass list">{list.map((c) => <button key={c.id} onClick={() => void open(c.id)}><span className="min0"><span className="t-row" style={{ display: "block" }}>{c.title ?? "Conversación"}</span><span className="muted t-small">{ago(c.last_message_at ?? c.created_at)}</span></span></button>)}</div>
        : <Empty title="Aún no tienes conversaciones guardadas." icon="chat" />) : <>
        <div className="stack" style={{ gap: 12 }} aria-live="polite">
          {/* Entering shows only Rumi's greeting; past conversations are one tap away. */}
          <div className="rumi-hello"><RumiAvatar size={132} /><h2 className="t-card">¡Hola! Soy Rumi</h2><p className="muted">Tu agente de reciclaje. ¿En qué te ayudo hoy?</p></div>
          {turns.map((t, i) => t.role === "user" ? <p key={i} className="bubble me">{t.content}</p>
            : <div key={i} className="rumi-turn"><RumiAvatar size={30} /><p className="bubble bot glass">{t.content}</p></div>)}
          {typing && <p className="muted t-small row" style={{ gap: 6 }}><i className="pulse" /> Rumi está escribiendo…</p>}
          <div ref={end} />
        </div>
        <div className="bottombar"><div>
          <form className="row" style={{ gap: 8 }} onSubmit={(e) => { e.preventDefault(); void send(text); }}>
            <span className="input-wrap min0"><input aria-label="Tu pregunta" placeholder="Pregúntame sobre reciclaje…" value={text} onChange={(e) => setText(e.target.value)} /></span>
            <button className="icon-btn" style={{ background: "var(--button)", color: "#fff", border: 0 }} aria-label="Enviar" disabled={typing}><Icon name="send" /></button>
          </form>
        </div></div>
      </>}
    </main>
  );
}
