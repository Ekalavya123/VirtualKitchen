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

  // New Recipe Tool: recipe detail (ingredients/nutrition/main process) — coexists with
  // `recipes` above, which still serves the legacy /api/v1/process-templates endpoints.
  recipeDetail: {
    byId: (recipeId: number) => `/api/v1/recipes/${recipeId}`,
    ingredients: (recipeId: number) => `/api/v1/recipes/${recipeId}/ingredients`,
    nutrition: (recipeId: number) => `/api/v1/recipes/${recipeId}/nutrition`,
    mainProcess: (recipeId: number) => `/api/v1/recipes/${recipeId}/main-process`,
  },

  // New Recipe Tool: Process (MAIN/SUBPROCESS) CRUD + copy, scoped under a recipe.
  processes: {
    list: (recipeId: number) => `/api/v1/recipes/${recipeId}/processes`,
    byId: (recipeId: number, processId: number) => `/api/v1/recipes/${recipeId}/processes/${processId}`,
    copy: (recipeId: number, processId: number) => `/api/v1/recipes/${recipeId}/processes/${processId}/copy`,
  },

  // AI-driven Process generation (semantic MAIN + subprocesses from recipe text) — async job only.
  processGeneration: {
    startJob: (recipeId: number) => `/api/v1/recipes/${recipeId}/processes/generate/jobs`,
    jobStatus: (recipeId: number, jobId: string) => `/api/v1/recipes/${recipeId}/processes/generate/jobs/${jobId}`,
  },

  recipeGeneration: {
    generateFlow: '/api/recipe/generate-flow',
    // Async job variant: start returns immediately (QUEUED), poll jobStatus for progress/results.
    startJob: '/api/recipe/generate-flow/jobs',
    jobStatus: (jobId: string) => `/api/recipe/generate-flow/jobs/${jobId}`,
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
    generateStep: (recipeId: number | string, stepId: string) =>
      `/api/recipes/${String(recipeId)}/visualization/steps/${stepId}/generate`,
    // Async job variant: start returns immediately (QUEUED), poll jobStatus for progress/results.
    startJob: (recipeId: number | string) => `/api/recipes/${String(recipeId)}/visualization/jobs`,
    jobStatus: (jobId: string) => `/api/recipes/visualization/jobs/${jobId}`,
  },

  // Process-model visualization (one image per STEP of a MAIN/SUBPROCESS) — async job only.
  processVisualization: {
    startJob: (recipeId: number, processId: number) =>
      `/api/v1/recipes/${recipeId}/processes/${processId}/visualization/jobs`,
    jobStatus: (recipeId: number, processId: number, jobId: string) =>
      `/api/v1/recipes/${recipeId}/processes/${processId}/visualization/jobs/${jobId}`,
  },

  // AI model management / credits
  ai: {
    models: '/api/v1/ai/models',
    myCredits: '/api/v1/users/me/ai-credits',
    myCreditHistory: '/api/v1/users/me/ai-credits/history',
  },
}
