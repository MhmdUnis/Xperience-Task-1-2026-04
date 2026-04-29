import { useEffect, useState } from "react";
import CreateEvent from "./pages/CreateEvent";
import HostDashboard from "./pages/HostDashboard";
import InviteePage from "./pages/InviteePage";

type Route =
  | { name: "create" }
  | { name: "host"; hostToken: string }
  | { name: "invite"; inviteeToken: string };

function parseHash(): Route {
  const h = window.location.hash.replace(/^#\/?/, "");
  if (h.startsWith("host/")) {
    return { name: "host", hostToken: h.substring("host/".length) };
  }
  if (h.startsWith("invite/")) {
    return { name: "invite", inviteeToken: h.substring("invite/".length) };
  }
  return { name: "create" };
}

export default function App() {
  const [route, setRoute] = useState<Route>(parseHash());

  useEffect(() => {
    const onHash = () => setRoute(parseHash());
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);

  return (
    <div className="container">
      <header style={{ marginBottom: "1.5rem" }}>
        <h1 style={{ fontSize: "1.4rem" }}>
          <a href="#/" style={{ textDecoration: "none", color: "#1f2433" }}>
            Event RSVP Manager
          </a>
        </h1>
      </header>

      {route.name === "create" && <CreateEvent />}
      {route.name === "host" && <HostDashboard hostToken={route.hostToken} />}
      {route.name === "invite" && <InviteePage inviteeToken={route.inviteeToken} />}
    </div>
  );
}
