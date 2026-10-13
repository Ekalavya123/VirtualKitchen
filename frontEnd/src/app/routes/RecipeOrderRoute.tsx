import { Navigate, useParams } from 'react-router-dom'
import { RecipeOrderPage } from '../../features/recipe-order'
import { useSignedIn } from '../session/signedIn'

/** One recipe order; RecipeOrderPage itself checks the `view` against the order's status (and redirects). */
export default function RecipeOrderRoute() {
  const { user } = useSignedIn()
  const { orderId: orderIdParam, view } = useParams()
  const orderId = Number(orderIdParam)

  if (!Number.isInteger(orderId) || orderId <= 0) {
    return <Navigate to="/kitchen/orders" replace />
  }

  return <RecipeOrderPage key={orderId} orderId={orderId} view={view} userId={user.id} />
}
