import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppLayout } from './layout/AppLayout'
import { AllFeedbackPage } from './pages/AllFeedbackPage'
import { CategoriesPage } from './pages/CategoriesPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { CouponsPage } from './pages/CouponsPage'
import { DashboardPage } from './pages/DashboardPage'
import { DiningAreasPage } from './pages/DiningAreasPage'
import { KitchenPage } from './pages/KitchenPage'
import { LoginPage } from './pages/LoginPage'
import { MenuImportPage } from './pages/MenuImportPage'
import { OrderTicketPrintPage } from './pages/OrderTicketPrintPage'
import { ProductAvailabilityPage } from './pages/ProductAvailabilityPage'
import { ProductModifiersPage } from './pages/ProductModifiersPage'
import { ProductsPage } from './pages/ProductsPage'
import { PostMealFeedbackPage } from './pages/PostMealFeedbackPage'
import { PublicMenuPage } from './pages/PublicMenuPage'
import { RegisterPage } from './pages/RegisterPage'
import { ReportsPage } from './pages/ReportsPage'
import { RestaurantSettingsPage } from './pages/RestaurantSettingsPage'
import { StaffPage } from './pages/StaffPage'
import { TabDetailPage } from './pages/TabDetailPage'
import { TablesPage } from './pages/TablesPage'
import { TabReceiptPrintPage } from './pages/TabReceiptPrintPage'

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/menu/:slug" element={<PublicMenuPage />} />
          <Route path="/menu/:slug/:tableId" element={<PublicMenuPage />} />
          <Route path="/feedback/:slug/:tabId" element={<PostMealFeedbackPage />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/orders/:orderId/print" element={<OrderTicketPrintPage />} />
            <Route path="/tabs/:tabId/print" element={<TabReceiptPrintPage />} />
            <Route element={<AppLayout />}>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/tables" element={<TablesPage />} />
              <Route path="/tabs/:tabId" element={<TabDetailPage />} />
              <Route path="/kitchen" element={<KitchenPage />} />
              <Route path="/checkout" element={<CheckoutPage />} />
              <Route path="/categories" element={<CategoriesPage />} />
              <Route path="/dining-areas" element={<DiningAreasPage />} />
              <Route path="/coupons" element={<CouponsPage />} />
              <Route path="/products" element={<ProductsPage />} />
              <Route path="/products/import" element={<MenuImportPage />} />
              <Route path="/products/:productId/modifiers" element={<ProductModifiersPage />} />
              <Route path="/products/:productId/availability" element={<ProductAvailabilityPage />} />
              <Route path="/reports" element={<ReportsPage />} />
              <Route path="/reports/feedback" element={<AllFeedbackPage />} />
              <Route path="/settings" element={<RestaurantSettingsPage />} />
              <Route path="/staff" element={<StaffPage />} />
            </Route>
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
