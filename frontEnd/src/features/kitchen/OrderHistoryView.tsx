import { useEffect, useState } from 'react'
import { OrderApi, type OrderResponse } from '../../api'
import './OrderHistoryView.css'

interface OrderHistoryViewProps {
  userId: number
}

export default function OrderHistoryView({ userId }: OrderHistoryViewProps) {
  const [orders, setOrders] = useState<OrderResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const fetchOrders = async () => {
      try {
        setLoading(true)
        setError('')
        const fetchedOrders = await OrderApi.getOrdersByUser(userId)
        setOrders(Array.isArray(fetchedOrders) ? fetchedOrders : [])
      } catch (fetchError) {
        console.error('Failed to fetch orders:', fetchError)
        setError(fetchError instanceof Error ? fetchError.message : 'Failed to load order history')
      } finally {
        setLoading(false)
      }
    }

    void fetchOrders()
  }, [userId])

  return (
    <div className="order-history-view">
      <div className="order-history-header">
        <h2>Order History</h2>
        <p>View your previous orders, totals, payment status, and order status.</p>
      </div>

      {loading ? (
        <div className="order-state">Loading orders...</div>
      ) : error ? (
        <div className="order-state order-error">{error}</div>
      ) : orders.length === 0 ? (
        <div className="order-state">No previous orders found.</div>
      ) : (
        <div className="order-history-list">
          {orders.map((order) => (
            <article key={order.orderId} className="order-card">
              <header className="order-card-header">
                <div>
                  <h3>Order #{order.orderId}</h3>
                  <p>{order.createdAt ? new Date(order.createdAt).toLocaleString() : 'N/A'}</p>
                </div>
                <div className="order-status-block">
                  <span className={`status-badge payment-${order.paymentStatus.toLowerCase()}`}>Payment: {order.paymentStatus}</span>
                  <span className={`status-badge order-${order.orderStatus.toLowerCase()}`}>Order: {order.orderStatus}</span>
                </div>
              </header>

              <div className="order-items">
                {order.items.map((item) => (
                  <div className="order-item-row" key={`${order.orderId}-${item.itemType}-${item.itemId}`}>
                    <span>{item.itemName}</span>
                    <span>
                      {item.quantity} {item.unit}
                    </span>
                    <span>${item.price.toFixed(2)}</span>
                    <span>${item.subTotal.toFixed(2)}</span>
                  </div>
                ))}
              </div>

              <footer className="order-card-footer">Total: ${order.totalAmount.toFixed(2)}</footer>
            </article>
          ))}
        </div>
      )}
    </div>
  )
}
