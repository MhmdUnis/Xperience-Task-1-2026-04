const API_BASE = "http://localhost:8280/api";

export type RsvpStatus = "NO_RESPONSE" | "YES" | "NO" | "MAYBE";
export type EventState = "OPEN" | "CLOSED" | "CANCELED";
export type Classification = "CONFIRMED" | "WAITLISTED" | null;

export interface CreateEventRequest {
  title: string;
  description?: string;
  location: string;
  startTime: string; // ISO LocalDateTime
  capacity: number;
}

export interface EventCreatedResponse {
  eventId: number;
  hostToken: string;
}

export interface InviteeRow {
  email: string;
  status: RsvpStatus;
  classification: Classification;
  waitlistSeq: number | null;
  inviteeToken: string;
}

export interface HostDashboard {
  eventId: number;
  title: string;
  description: string | null;
  location: string;
  startTime: string;
  capacity: number;
  state: EventState;
  locked: boolean;
  counts: {
    confirmed: number;
    waitlisted: number;
    no: number;
    maybe: number;
    noResponse: number;
  };
  invitees: InviteeRow[];
}

export interface InviteeView {
  eventTitle: string;
  eventDescription: string | null;
  eventLocation: string;
  startTime: string;
  eventState: EventState;
  locked: boolean;
  email: string;
  myStatus: RsvpStatus;
  myClassification: Classification;
  myWaitlistSeq: number | null;
}

interface ApiError {
  error: string;
  message: string;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });
  if (!res.ok) {
    let body: ApiError | undefined;
    try {
      body = await res.json();
    } catch {
      // ignore
    }
    throw new Error(body?.message ?? `HTTP ${res.status}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const api = {
  createEvent: (req: CreateEventRequest) =>
    request<EventCreatedResponse>("/events", {
      method: "POST",
      body: JSON.stringify(req),
    }),

  getDashboard: (hostToken: string) =>
    request<HostDashboard>(`/host/${hostToken}/dashboard`),

  addInvitee: (hostToken: string, email: string) =>
    request<{ invitationId: number; email: string; inviteeToken: string }>(
      `/host/${hostToken}/invitees`,
      { method: "POST", body: JSON.stringify({ email }) }
    ),

  closeEvent: (hostToken: string) =>
    request<void>(`/host/${hostToken}/close`, { method: "POST" }),

  reopenEvent: (hostToken: string) =>
    request<void>(`/host/${hostToken}/reopen`, { method: "POST" }),

  cancelEvent: (hostToken: string) =>
    request<void>(`/host/${hostToken}/cancel`, { method: "POST" }),

  getInvite: (inviteeToken: string) =>
    request<InviteeView>(`/invite/${inviteeToken}`),

  respond: (inviteeToken: string, response: "YES" | "NO" | "MAYBE") =>
    request<InviteeView>(`/invite/${inviteeToken}/rsvp`, {
      method: "PUT",
      body: JSON.stringify({ response }),
    }),
};
