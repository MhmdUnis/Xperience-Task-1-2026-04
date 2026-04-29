import { useEffect, useState } from "react";
import { api, type InviteeView } from "../api";

export default function InviteePage({ inviteeToken }: { inviteeToken: string }) {
  const [data, setData] = useState<InviteeView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function load() {
    try {
      setData(await api.getInvite(inviteeToken));
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load");
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inviteeToken]);

  async function respond(value: "YES" | "NO" | "MAYBE") {
    setSubmitting(true);
    setError(null);
    try {
      const next = await api.respond(inviteeToken, value);
      setData(next);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to submit");
    } finally {
      setSubmitting(false);
    }
  }

  if (!data) {
    return <div className="card">{error ? <div className="error">{error}</div> : "Loading…"}</div>;
  }

  const disabled = submitting || data.eventState !== "OPEN" || data.locked;

  let statusBlock: React.ReactNode;
  if (data.myStatus === "NO_RESPONSE") {
    statusBlock = <p className="muted">You haven’t responded yet.</p>;
  } else if (data.myStatus === "YES" && data.myClassification === "CONFIRMED") {
    statusBlock = <p>You’re <span className="tag green">Confirmed</span>.</p>;
  } else if (data.myStatus === "YES" && data.myClassification === "WAITLISTED") {
    statusBlock = <p>You’re on the waitlist at position <span className="tag amber">#{data.myWaitlistSeq}</span>.</p>;
  } else if (data.myStatus === "NO") {
    statusBlock = <p>You’ve <span className="tag red">Declined</span>.</p>;
  } else if (data.myStatus === "MAYBE") {
    statusBlock = <p>You said <span className="tag gray">Maybe</span>.</p>;
  }

  return (
    <>
      {error && <div className="error">{error}</div>}

      <div className="card">
        <h2>{data.eventTitle}</h2>
        <div className="muted">{new Date(data.startTime).toLocaleString()} · {data.eventLocation}</div>
        {data.eventDescription && <p style={{ marginTop: 12 }}>{data.eventDescription}</p>}
        <p className="muted" style={{ marginTop: 12 }}>Invited as <strong>{data.email}</strong></p>
      </div>

      <div className="card">
        <h3>Your response</h3>
        {statusBlock}

        {data.eventState === "CANCELED" ? (
          <div className="error">This event has been canceled.</div>
        ) : data.locked ? (
          <div className="error">This event has started — RSVPs are locked.</div>
        ) : data.eventState === "CLOSED" ? (
          <div className="error">This event is closed to further responses.</div>
        ) : (
          <div className="row" style={{ marginTop: 12 }}>
            <button onClick={() => respond("YES")} disabled={disabled}>Yes</button>
            <button className="secondary" onClick={() => respond("MAYBE")} disabled={disabled}>Maybe</button>
            <button className="danger" onClick={() => respond("NO")} disabled={disabled}>No</button>
          </div>
        )}
      </div>
    </>
  );
}
