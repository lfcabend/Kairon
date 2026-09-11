import { Navigate, Route, Routes } from "react-router-dom";

import { AppLayout } from "@/components/AppLayout";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import AccountPage from "@/features/auth/AccountPage";
import LoginPage from "@/features/auth/LoginPage";
import RegisterPage from "@/features/auth/RegisterPage";
import { JournalDayView } from "@/features/journal/JournalDayView";
import { JournalSearchPage } from "@/features/journal/JournalSearchPage";
import { DayView } from "@/features/todo/DayView";

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<Navigate to="/day" replace />} />
          <Route path="day/:date?" element={<DayView />} />
          <Route path="journal/:date?" element={<JournalDayView />} />
          <Route path="journal/search" element={<JournalSearchPage />} />
          <Route path="account" element={<AccountPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
