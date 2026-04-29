import { useEffect, useState } from "react";
import { api, type HostDashboard, type InviteeRow } from "../api";

const POLL_MS = 3000;

export default function HostDashboardPage({ hostToken }: { hostToken: string }) {
  const [data, setData] = useState<HostDashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [emailToInvite, setEmailToInvite] = useState("");
  const [busy, setBusy] = useState(false);

  async function refresh() {
    try {
      const next = await api.getDashboard(hostToken);
      setData(next);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load");
    }
  }

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, POLL_MS);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hostToken]);

  async function addInvitee(e: React.FormEvent) {
    e.preventDefault();
    if (!emailToInvite.trim()) return;
    setBusy(true);
    try {
      await api.addInvitee(hostToken, emailToInvite.trim());
      setEmailToInvite("");
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add invitee");
    } finally {
      setBusy(false);
    }
  }

  async function close() { setBusy(true); try { await api.closeEvent(hostToken); await refresh(); } catch (e) { setError(String(e)); } finally { setBusy(false); } }
  async function reopen() { setBusy(true); try { await api.reopenEvent(hostToken); await refresh(); } catch (e) { setError(String(e)); } finally { setBusy(false); } }
  async function cancel() {
    if (!confirm("Cancel this event? This cannot be undone.")) return;
    setBusy(true);
    try { await api.cancelEvent(hostToken); await refresh(); } catch (e) { setError(String(e)); } finally { setBusy(false); }
  }

  if (!data) {
    return <div className="card">{error ? <div className="error">{error}</div> : "Loading…"}</div>;
  }

  const stateTag = (() => {
    if (data.state === "CANCELED") return <span className="tag red">CANCELED</span>;
    if (data.locked) return <span className="tag amber">LOCKED (started)</span>;
    if (data.state === "CLOSED") return <span className="tag amber">CLOSED</span>;
    return <span className="tag green">OPEN</span>;
  })();

  return (
    <>
      {error && <div className="error">{error}</div>}

      <div className="card">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12 }}>
          <div>
            <h2 style={{ marginBottom: 4 }}>{data.title}</h2>
            <div className="muted">{new Date(data.startTime).toLocaleString()} · {data.location}</div>
            {data.description && <p style={{ marginTop: 12 }}>{data.description}</p>}
          </div>
          <div>{stateTag}</div>
        </div>
      </div>

      <div className="card">
        <div className="counts">
          <Tile num={data.counts.confirmed} lbl={`Confirmed / ${data.capacity}`} />
          <Tile num={data.counts.waitlisted} lbl="Waitlisted" />
          <Tile num={data.counts.maybe} lbl="Maybe" />
          <Tile num={data.counts.no} lbl="Declined" />
          <Tile num={data.counts.noResponse} lbl="No response" />
        </div>
      </div>

      <div className="card">
        <h3>Add invitee</h3>
        <form onSubmit={addInvitee} className="row" style={{ alignItems: "flex-end" }}>
          <div style={{ flex: 1, minWidth: 220 }}>
            <input
              type="email"
              placeholder="email@example.com"
              value={emailToInvite}
              onChange={(e) => setEmailToInvite(e.target.value)}
              disabled={busy || data.state !== "OPEN" || data.locked}
              style={{ marginBottom: 0 }}
            />
          </div>
          <button disabled={busy || data.state !== "OPEN" || data.locked}>Add invitee</button>
        </form>
        {data.state !== "OPEN" && <p className="muted" style={{ marginTop: 8 }}>Event is not accepting new invitees.</p>}
      </div>

      <div className="card">
        <h3>Invitees</h3>
        {data.invitees.length === 0 ? (
          <p className="muted">No invitees yet.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Email</th>
                <th>Status</th>
                <th>Invite link</th>
              </tr>
            </thead>
            <tbody>
              {data.invitees.map((r) => <InviteeRowView key={r.inviteeToken} row={r} />)}
            </tbody>
          </table>
        )}
      </div>

      <div className="card">
        <h3>Event actions</h3>
        <div className="row">
          {data.state === "OPEN" && <button className="secondary" onClick={close} disabled={busy}>Close to RSVPs</button>}
          {data.state === "CLOSED" && <button className="secondary" onClick={reopen} disabled={busy}>Reopen</button>}
          {data.state !== "CANCELED" && <button className="danger" onClick={cancel} disabled={busy}>Cancel event</button>}
        </div>
      </div>

      <p className="muted" style={{ textAlign: "center" }}>Auto-refreshing every {POLL_MS / 1000}s.</p>
    </>
  );
}

function Tile({ num, lbl }: { num: number | string; lbl: string }) {
  return (
    <div className="count-tile">
      <div className="num">{num}</div>
      <div className="lbl">{lbl}</div>
    </div>
  );
}

function InviteeRowView({ row }: { row: InviteeRow }) {
  const inviteUrl = `${window.location.origin}${window.location.pathname}#/invite/${row.inviteeToken}`;
  let tag: React.ReactNode = <span className="tag gray">No response</span>;
  if (row.status === "YES" && row.classification === "CONFIRMED") tag = <span className="tag green">Confirmed</span>;
  else if (row.status === "YES" && row.classification === "WAITLISTED") tag = <span className="tag amber">Waitlist #{row.waitlistSeq}</span>;
  else if (row.status === "NO") tag = <span className="tag red">Declined</span>;
  else if (row.status === "MAYBE") tag = <span className="tag gray">Maybe</span>;

  return (
    <tr>
      <td>{row.email}</td>
      <td>{tag}</td>
      <td><a href={inviteUrl} className="invite-link">{inviteUrl}</a></td>
    </tr>
  );
}
