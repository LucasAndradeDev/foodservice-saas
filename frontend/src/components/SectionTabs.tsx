import { NavLink } from 'react-router-dom'

interface SectionTab {
  to: string
  label: string
}

export function SectionTabs({ tabs }: { tabs: SectionTab[] }) {
  return (
    <div className="mb-4 flex border-b border-gray-200 dark:border-white/10 sm:gap-4">
      {tabs.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end
          className={({ isActive }) =>
            `flex-1 border-b-2 px-2 py-2.5 text-center text-sm font-medium transition sm:flex-none sm:px-1 sm:py-0 sm:pb-2 sm:text-left ${
              isActive
                ? 'border-brand-600 text-brand-700 dark:border-brand-400 dark:text-brand-400'
                : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-stone-400 dark:hover:text-stone-200'
            }`
          }
        >
          {tab.label}
        </NavLink>
      ))}
    </div>
  )
}
