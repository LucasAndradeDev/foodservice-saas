import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppLayout } from './layout/AppLayout'
import { CategoriesPage } from './pages/CategoriesPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { DashboardPage } from './pages/DashboardPage'
import { KitchenPage } from './pages/KitchenPage'
import { LoginPage } from './pages/LoginPage'
import { OrderTicketPrintPage } from './pages/OrderTicketPrintPage'
import { ProductsPage } from './pages/ProductsPage'
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
              <Route path="/products" element={<ProductsPage />} />
              <Route path="/reports" element={<ReportsPage />} />
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
