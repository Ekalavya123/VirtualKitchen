import { apiGet, apiPost } from './client'
import { API } from './endpoints'
import type { ItemType, UnitType } from '../types/kitchen'

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'FAILED'
export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED'

export interface OrderItemRequest {
  itemId: number
  itemType: ItemType
  itemName: string
  quantity: number
  unit: UnitType
  price: number
}

export interface OrderCreateRequest {
  /** Optional: the backend takes the buyer from the session token; when sent it must be the signed-in user. */
  userId?: number
  items: OrderItemRequest[]
}

export interface OrderItemResponse {
  itemId: number
  itemType: ItemType
  itemName: string
  quantity: number
  unit: UnitType
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
