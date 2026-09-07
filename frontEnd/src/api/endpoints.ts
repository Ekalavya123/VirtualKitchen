/**
 * Centralized API Endpoints
 * All endpoint URLs are defined here to avoid hardcoding in components
 */

export const API = {
  // Authentication & Users
  auth: {
    users: '/api/v1/users',
    userByEmail: (email: string) => `/api/v1/users/email/${email}`,

    signup: '/api/v1/auth/signup',
    login: '/api/v1/auth/login',
    google: '/api/v1/auth/google',
    me: '/api/v1/auth/me',

    sendEmailOtp: '/api/v1/auth/email/send-otp',
    verifyEmailOtp: '/api/v1/auth/email/verify-otp',

    sendLoginOtp: '/api/v1/auth/login/otp/send',
    verifyLoginOtp: '/api/v1/auth/login/otp/verify',

    forgotPassword: '/api/v1/auth/password/forgot',
    verifyPasswordResetOtp: '/api/v1/auth/password/verify-otp',
    resetPassword: '/api/v1/auth/password/reset',
  },

  // Kitchens
  kitchen: {
    list: '/api/v1/kitchens',
    byId: (id: number) => `/api/v1/kitchens/${id}`,
    byOwnerId: (ownerId: number) => `/api/v1/kitchens/owner/${ownerId}`,
  },

  // Inventory
  inventory: {
    byKitchenId: (kitchenId: number) => `/api/v1/inventory/kitchen/${kitchenId}`,
  },

  // Shop Items
  shop: {
    ingredients: '/api/v1/ingredients',
    equipment: '/api/v1/equipments',
  },

  // Orders
  orders: {
    list: '/api/v1/orders',
    byUserId: (userId: number) => `/api/v1/orders/user/${userId}`,
  },

  // Recipes / Process Templates
  recipes: {
    list: '/api/v1/process-templates',
    byId: (id: number) => `/api/v1/process-templates/${id}`,
    byUserId: (userId: number) => `/api/v1/process-templates/user/${userId}`,
    global: (userId: number) => `/api/v1/process-templates/global/${userId}`,
    visibility: (id: number) => `/api/v1/process-templates/${id}/visibility`,
    copy: (id: number) => `/api/v1/process-templates/${id}/copy`,
  },

  recipeGeneration: {
    generateFlow: '/api/recipe/generate-flow',
  },

  // Flows
  flows: {
    byId: (id: number | string) => `/api/v1/flows/${id}`,
  },

  // Visualizations
  visualizations: {
    byId: (id: number | string) => `/api/v1/visualizations/${String(id)}`,
  },

  // Recipe step visualization assets (image/video prompts per step)
  recipeVisualization: {
    generate: (recipeId: number | string) => `/api/recipes/${String(recipeId)}/visualization/generate`,
  },
}
