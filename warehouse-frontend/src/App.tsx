import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { Layout } from './components/Layout'
import { IngredientsPage } from './pages/IngredientsPage'
import { NotAuthenticatedPage } from './pages/NotAuthenticatedPage'
import { PurchasesPage } from './pages/PurchasesPage'
import { RecipesPage } from './pages/RecipesPage'
import { SsoPage } from './pages/SsoPage'
import { SuppliersPage } from './pages/SuppliersPage'

function HomeRoute() {
  const { isAuthenticated } = useAuth()
  return isAuthenticated ? <Navigate to="/ingredients" replace /> : <NotAuthenticatedPage />
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<HomeRoute />} />
          <Route path="/sso" element={<SsoPage />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<Layout />}>
              <Route path="/ingredients" element={<IngredientsPage />} />
              <Route path="/suppliers" element={<SuppliersPage />} />
              <Route path="/purchases" element={<PurchasesPage />} />
              <Route path="/recipes" element={<RecipesPage />} />
            </Route>
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
