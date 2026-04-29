import { useState } from "react";
import { api } from "../api";

export default function CreateEvent() {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [location, setLocation] = useState("");
  const [startTime, setStartTime] = useState("");
  const [capacity, setCapacity] = useState(50);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<{ eventId: number; hostToken: string } | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const res = await api.createEvent({
        title,
        description: description || undefined,
        location,
        startTime, // datetime-local gives "YYYY-MM-DDTHH:mm" — Spring parses as LocalDateTime
        capacity,
      });
      setCreated(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setSubmitting(false);
    }
  }

  if (created) {
    const hostUrl = `${window.location.origin}${window.location.pathname}#/host/${created.hostToken}`;
    return (
      <div className="card">
        <h2>Event created</h2>
        <p className="muted">Save this host link — it is the only way to access your dashboard.</p>
        <p>
          <a href={hostUrl} className="invite-link">{hostUrl}</a>
        </p>
        <div className="row">
          <a href={hostUrl}><button>Go to dashboard</button></a>
          <button className="secondary" onClick={() => setCreated(null)}>Create another</button>
        </div>
      </div>
    );
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2>Create an event</h2>
      {error && <div className="error">{error}</div>}

      <label>Title</label>
      <input value={title} onChange={(e) => setTitle(e.target.value)} required />

      <label>Description (optional)</label>
      <textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />

      <label>Location</label>
      <input value={location} onChange={(e) => setLocation(e.target.value)} required />

      <label>Start time</label>
      <input type="datetime-local" value={startTime} onChange={(e) => setStartTime(e.target.value)} required />

      <label>Capacity</label>
      <input type="number" min={1} value={capacity} onChange={(e) => setCapacity(Number(e.target.value))} required />

      <button disabled={submitting}>{submitting ? "Creating…" : "Create event"}</button>
    </form>
  );
}
