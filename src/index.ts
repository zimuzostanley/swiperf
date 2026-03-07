import m from 'mithril'
import { App } from './components/App'
import { CSS } from './styles'

// Inject styles
const style = document.createElement('style')
style.textContent = CSS
document.head.appendChild(style)

// Add tooltip element
const tip = document.createElement('div')
tip.className = 'tooltip'
tip.id = 'tip'
document.body.appendChild(tip)

// Mount app
m.mount(document.getElementById('app')!, App)
