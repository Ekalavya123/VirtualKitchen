import { apiGet, apiPost } from './client'
import { API } from './endpoints'

export type OrderItemType = 'INGREDIENT' | 'EQUIPMENT'
export type OrderUnitType = 'KG' | 'GRAM' | 'LITER' | 'ML' | 'COUNT'
export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'FAILED'
export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED'

export interface OrderItemRequest {
  itemId: number
  itemType: OrderItemType
  itemName: string
  quantity: number
  unit: OrderUnitType
  price: number
}

export interface OrderCreateRequest {
  /** Optional: the backend takes the buyer from the session token; when sent it must be the signed-in user. */
  userId?: number
  items: OrderItemRequest[]
}

export interface OrderItemResponse {
  itemId: number
  itemType: OrderItemType
  itemName: string
  quantity: number
  unit: OrderUnitType
  price: number
  subTotal: number
}

export interface OrderResponse {
  orderId: number
  /** Human-readable order number ("SO-000045"); absent on orders from older backends. */
  orderCode?: string
  userId: number
  items: OrderItemResponse[]
  totalAmount: number
  orderStatus: OrderStatus
  paymentStatus: PaymentStatus
  createdAt: string
}

export const OrderApi = {
  async createOrder(data: OrderCreateRequest): Promise<OrderResponse> {
    return apiPost<OrderResponse>(API.orders.list, data)
  },

  async getOrdersByUser(userId: number): Promise<OrderResponse[]> {
    return apiGet<OrderResponse[]>(API.orders.byUserId(userId))
  },
}
