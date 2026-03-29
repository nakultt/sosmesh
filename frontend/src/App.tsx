import { BrowserRouter, Routes, Route } from "react-router-dom";
import Dashboard from "./pages/Dashboard";
import AlertDetail from "./pages/AlertDetail";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/alert/:alertId" element={<AlertDetail />} />
      </Routes>
    </BrowserRouter>
  );
}
