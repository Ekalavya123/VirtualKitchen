import './AppFooter.css'

export default function AppFooter() {
  return (
    <footer className="app-footer">
      <div className="app-footer-content">
        <p>&copy; {new Date().getFullYear()} Virtual Kitchen. All rights reserved.</p>
        <div className="app-footer-links">
          <a href="#privacy">Privacy Policy</a>
          <span>•</span>
          <a href="#terms">Terms of Service</a>
          <span>•</span>
          <a href="#contact">Contact Us</a>
        </div>
      </div>
    </footer>
  )
}
