import React from "react";
import ReactDOM from "react-dom/client";
import "@fontsource-variable/inter"; // self-hosted enterprise typeface (offline; no CDN)
import { App } from "./App";
import "./styles/tokens.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
