import { AnimatePresence, motion } from 'framer-motion'
import { useLocation } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { LoginPage } from './LoginPage'
import { RegisterPage } from './RegisterPage'

const EASE_OUT: [number, number, number, number] = [0.16, 1, 0.3, 1]

export function AuthRoutes() {
  const location = useLocation()
  const isRegister = location.pathname === '/register'

  return (
    <AuthLayout>
      <AnimatePresence mode="wait" initial={false}>
        <motion.div
          key={isRegister ? 'register' : 'login'}
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -16 }}
          transition={{ duration: 0.3, ease: EASE_OUT }}
        >
          {isRegister ? <RegisterPage /> : <LoginPage />}
        </motion.div>
      </AnimatePresence>
    </AuthLayout>
  )
}
