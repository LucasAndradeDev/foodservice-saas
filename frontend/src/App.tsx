import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AdminAuthProvider } from './admin/AdminAuthContext'
import { AdminForgotPasswordPage } from './admin/AdminForgotPasswordPage'
import { AdminLoginPage } from './admin/AdminLoginPage'
import { AdminProtectedRoute } from './admin/AdminProtectedRoute'
import { AdminResetPasswordPage } from './admin/AdminResetPasswordPage'
import { AdminRestaurantsPage } from './admin/AdminRestaurantsPage'
import { AuthProvider } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AuthLayout } from './components/AuthLayout'
import { AppLayout } from './layout/AppLayout'
import { AllFeedbackPage } from './pages/AllFeedbackPage'
import { AuthRoutes } from './pages/AuthRoutes'
import { CashRegisterPage } from './pages/CashRegisterPage'
import { CategoriesPage } from './pages/CategoriesPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { CombosPage } from './pages/CombosPage'
import { ComboFormPage } from './pages/ComboFormPage'
import { CouponsPage } from './pages/CouponsPage'
import { DashboardPage } from './pages/DashboardPage'
import { DiningAreasPage } from './pages/DiningAreasPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { HappyHourPage } from './pages/HappyHourPage'
import { KitchenPage } from './pages/KitchenPage'
import { MenuImportPage } from './pages/MenuImportPage'
import { OrderTicketPrintPage } from './pages/OrderTicketPrintPage'
import { ProductAvailabilityPage } from './pages/ProductAvailabilityPage'
import { ProductModifiersPage } from './pages/ProductModifiersPage'
import { ProductsPage } from './pages/ProductsPage'
import { PostMealFeedbackPage } from './pages/PostMealFeedbackPage'
import { PrivacyPolicyPage } from './pages/PrivacyPolicyPage'
import { PublicMenuPage } from './pages/PublicMenuPage'
import { PublicReservationStatusPage } from './pages/PublicReservationStatusPage'
import { ReportsPage } from './pages/ReportsPage'
import { ReservationsPage } from './pages/ReservationsPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { RestaurantSettingsPage } from './pages/RestaurantSettingsPage'
import { StaffPage } from './pages/StaffPage'
import { TabDetailPage } from './pages/TabDetailPage'
import { TablesPage } from './pages/TablesPage'
import { TabReceiptPrintPage } from './pages/TabReceiptPrintPage'
import { TermsOfServicePage } from './pages/TermsOfServicePage'
import { VerifyEmailPage } from './pages/VerifyEmailPage'
import { ThemeProvider } from './theme/ThemeContext'

function App() {
  return (
    <ThemeProvider>
      <BrowserRouter>
        <AuthProvider>
          <AdminAuthProvider>
            <Routes>
            <Route path="/admin/login" element={<AdminLoginPage />} />
            <Route
              path="/admin/forgot-password"
              element={
                <AuthLayout>
                  <AdminForgotPasswordPage />
                </AuthLayout>
              }
            />
            <Route
              path="/admin/reset-password"
              element={
                <AuthLayout>
                  <AdminResetPasswordPage />
                </AuthLayout>
              }
            />
            <Route element={<AdminProtectedRoute />}>
              <Route path="/admin/restaurants" element={<AdminRestaurantsPage />} />
            </Route>
            <Route path="/login" element={<AuthRoutes />} />
            <Route path="/register" element={<AuthRoutes />} />
            <Route
              path="/forgot-password"
              element={
                <AuthLayout>
                  <ForgotPasswordPage />
                </AuthLayout>
              }
            />
            <Route
              path="/reset-password"
              element={
                <AuthLayout>
                  <ResetPasswordPage />
                </AuthLayout>
              }
            />
            <Route
              path="/verify-email"
              element={
                <AuthLayout>
                  <VerifyEmailPage />
                </AuthLayout>
              }
            />
            <Route path="/terms" element={<TermsOfServicePage />} />
            <Route path="/privacy" element={<PrivacyPolicyPage />} />
            <Route path="/menu/:slug" element={<PublicMenuPage />} />
            <Route path="/menu/:slug/:tableId" element={<PublicMenuPage />} />
            <Route path="/feedback/:slug/:tabId" element={<PostMealFeedbackPage />} />
            <Route path="/reservations/status/:token" element={<PublicReservationStatusPage />} />
            <Route element={<ProtectedRoute />}>
              <Route path="/orders/:orderId/print" element={<OrderTicketPrintPage />} />
              <Route path="/tabs/:tabId/print" element={<TabReceiptPrintPage />} />
              <Route element={<AppLayout />}>
                <Route path="/" element={<DashboardPage />} />
                <Route path="/tables" element={<TablesPage />} />
                <Route path="/tabs/:tabId" element={<TabDetailPage />} />
                <Route path="/kitchen" element={<KitchenPage />} />
                <Route path="/checkout" element={<CheckoutPage />} />
                <Route path="/cash-register" element={<CashRegisterPage />} />
                <Route path="/reservations" element={<ReservationsPage />} />
                <Route path="/categories" element={<CategoriesPage />} />
                <Route path="/dining-areas" element={<DiningAreasPage />} />
                <Route path="/coupons" element={<CouponsPage />} />
                <Route path="/happy-hour" element={<HappyHourPage />} />
                <Route path="/products" element={<ProductsPage />} />
                <Route path="/products/import" element={<MenuImportPage />} />
                <Route path="/products/:productId/modifiers" element={<ProductModifiersPage />} />
                <Route path="/products/:productId/availability" element={<ProductAvailabilityPage />} />
                <Route path="/combos" element={<CombosPage />} />
                <Route path="/combos/new" element={<ComboFormPage />} />
                <Route path="/combos/:productId" element={<ComboFormPage />} />
                <Route path="/reports" element={<ReportsPage />} />
                <Route path="/reports/feedback" element={<AllFeedbackPage />} />
                <Route path="/settings" element={<RestaurantSettingsPage />} />
                <Route path="/staff" element={<StaffPage />} />
              </Route>
            </Route>
          </Routes>
          </AdminAuthProvider>
        </AuthProvider>
      </BrowserRouter>
    </ThemeProvider>
  )
}

export default App
