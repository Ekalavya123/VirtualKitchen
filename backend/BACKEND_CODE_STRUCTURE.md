# Backend Code Structure

This document explains the backend package structure of the Virtual Kitchen application and the responsibility of each package. It reflects the actual code layout — a **feature-based** structure, not a flat layer-based one.

## Tech stack

- Java 17
- Spring Boot 4.0.6 (Web, Security, Data MongoDB, Actuator, Validation, Mail)
- MongoDB (Spring Data MongoDB) as the primary datastore
- Lombok for boilerplate reduction on models/DTOs
- JWT-based authentication (custom `JwtService` + `JwtAuthenticationFilter`)
- Outbound REST clients to Google Gemini, OpenAI, Ollama, DrawThings (AI/image generation) and Supabase (storage)
- Maven (with the Maven Wrapper, `mvnw`/`mvnw.cmd`)

## Overall architecture

The backend is a layered Spring Boot application, but the layers are nested **inside** each feature package rather than the other way around:

```text
backend/src/main/java/com/processVisualisation/virtualKitchen/
├── VirtualKitchenApplication.java   (Spring Boot entry point)
├── MongoVerifyRunner.java           (startup MongoDB connectivity check)
├── DummyDataLoader.java             (manual demo-data seeding helper)
├── ai/            — AI chat + recipe generation + AI-driven visualization jobs
├── auth/          — authentication, users, OTP, JWT
├── common/        — cross-cutting infrastructure shared by every feature
├── kitchen/       — kitchens, kitchen-scoped inventory, orders
├── recipe/        — recipes, templates, steps, executions, flow graphs
├── restclient/    — outbound HTTP clients to external AI/storage providers
└── store/         — catalog of ingredients/equipment, inventory, item cost history
```

Within each feature package (`ai`, `auth`, `kitchen`, `recipe`, `store`), the same five sub-layers recur:

```text
<feature>/
├── controller/   REST endpoints for the feature
├── service/      business logic (interface + *Impl)
├── repository/   Spring Data MongoDB repositories
├── model/        persisted documents and enums
└── dto/          request/response payloads
```

`restclient` follows a variant of this shape (`client/`, `config/`, `dto/`, `exception/`) since it talks to external providers rather than persisting data. `common` has no controller/service split — it holds shared infrastructure instead (see below).

## Typical request flow

```text
Client -> Controller -> Service -> Repository -> MongoDB
```

The response travels back through the same layers, with the service layer mapping entities to DTOs (often via a `common/mapper` class) before the controller returns them.

## Feature packages

### `ai/`

AI chat, AI-driven recipe-flow generation, and asynchronous AI visualization (image/clip) generation for recipe steps.

- **controller** — `AIController` (chat), `AIRecipeGenerationController` (text → structured recipe flow), `AIResponseController` (CRUD over logged AI interactions), `AIVisualizationController` (trigger flow visualization).
- **service** — `IAIService`/`AIServiceImpl` (send a chat prompt to the configured provider), `IAIResponseService`/`AIResponseService` (persist/query AI interaction logs), `AIRecipeFlowPromptBuilder` + `AIRecipeValidator` + `AIRecipeFlowValidationResult` (prompt construction and validation for AI-generated recipe graphs), `AIRecipeGenerationService` (orchestrates generation + validation + retry), `AIVisualizationPromptBuilder` (builds per-step image/video prompts with continuity from the previous step), `IAIService`-adjacent `AIVisualizationService`/`AIVisualizationServiceImpl` (generates clip placeholders), `AIRecipeVisualizationService` (resolves/generates visualization assets per step), `VisualizationJobService` (orchestrates async, per-step visualization generation jobs on the `common/concurrent` `TaskPool` framework so slow AI/image calls never block the request thread).
- **repository** — `AIResponseRepository`, `AIVisualizationAssetRepository`, `AIVisualizationClipRepository`, `VisualizationJobRepository`.
- **model** — `AIResponseDocument`, `VisualizationAsset` (+ `VisualizationAssetType` enum), `VisualizationClip`, `VisualizationJob` (+ `VisualizationJobStatus` enum).
- **dto** — chat request/response, AI response record, visualization request/response and clip response DTOs.

### `auth/`

Email/password and Google OAuth authentication, OTP verification, JWT issuance, and user account management.

- **controller** — `AuthController` (`/api/v1/auth`: signup, login, Google OAuth, OTP send/verify, password reset, current user), `UserController` (`/api/v1/users`: user CRUD).
- **security** — `JwtAuthenticationFilter`, a once-per-request filter that extracts a Bearer JWT and populates the Spring Security context.
- **service** — `IAuthService`/`AuthServiceImpl` (full auth lifecycle, provisions a default kitchen on signup), `IUserService`/`UserServiceImpl` (user CRUD), `JwtService` (issues/validates signed JWTs), `OtpService`/`OtpServiceImpl` (OTP generation, expiry, attempt limits, resend throttling), `EmailService`/`EmailServiceImpl` (SMTP delivery of OTP emails), `GoogleTokenVerifierService` + `GoogleUserInfo` (verifies Google ID tokens).
- **repository** — `UserRepository`, `OtpRepository`.
- **model** — `User`, `Otp`, and enums `AuthProvider`, `OtpPurpose`, `UserStatus`, `UserType`.
- **dto** — signup/login/reset/verify-OTP request DTOs, `AuthResponseDTO`, `UserResponseDTO`, `UserRequestDTO`/`UserUpdateDTO`.

### `kitchen/`

Kitchen management, kitchen-scoped inventory, and order placement/history.

- **controller** — `KitchenController`, `KitchenInventoryController`, `InventoryController`, `OrderController`.
- **service** — `IKitchenService`/`KitchenServiceImpl`, `IKitchenInventoryService`/`KitchenInventoryServiceImpl`, `IInventoryService`/`InventoryServiceImpl`, `IOrderService`/`OrderServiceImpl` (validates items, computes totals, syncs ordered items into kitchen inventory).
- **repository** — `KitchenRepository`, `KitchenInventoryRepository`, `InventoryRepository`, `OrderRepository`.
- **model** — `Kitchen`, `KitchenInventory`, `Order` (+ `OrderItem`), enums `OrderStatus`, `PaymentStatus`.
- **dto** — request/response DTOs for kitchens, kitchen-inventory associations, inventory, and orders/order items.

### `recipe/`

The largest feature package: recipe templates and their steps, the reusable step-definition catalog, recipe executions and per-step executions, ingredient/equipment usage tracking, and the saved process-flow graph (nodes/edges/viewport) that backs the visual editor.

- **controller** — `RecipeTemplateController` (CRUD + visibility + copy-to-user, ownership-enforced), `RecipeTemplateStepController`, `RecipeStepDefinitionController`, `RecipeExecutionController`, `RecipeStepExecutionController`, `RecipeEquipmentUsageController`, `RecipeIngredientUsageController`, `RecipeController` (saved flow graph CRUD by flow id), `RecipeVisualizationController` (sync + async AI visualization triggering).
- **service** — `IProcessTemplateService`/`RecipeTemplateServiceImpl`, `IProcessTemplateStepService`/`ProcessTemplateStepServiceImpl`, `IStepDefinitionService`/`RecipeStepDefinitionServiceImpl`, `IProcessExecutionService`/`RecipeExecutionServiceImpl`, `IStepExecutionService`/`RecipeStepExecutionServiceImpl`, `IProcessEquipmentUsageService`/`RecipeEquipmentUsageServiceImpl`, `IProcessIngredientUsageService`/`RecipeIngredientUsageServiceImpl`, `RecipeService` (converts raw frontend flow-graph maps into typed documents and persists/retrieves them).
- **repository** — one Spring Data MongoDB repository per model below.
- **model** — `RecipeTemplate`, `RecipeTemplateStep`, `RecipeStepDefinition`, `RecipeExecution`, `RecipeStepExecution`, `RecipeEquipmentUsage`, `RecipeIngredientUsage`, `Recipe` (the saved flow graph, with nested `NodeDocument`/`EdgeDocument`/`PositionDocument`/`MeasuredDocument`/`ViewportDocument`), and enums `RecipeStatus`, `RecipeStepStatus`, `UnitType`, `Visibility`, `AssetType`.
- **dto** — request/response DTOs mirroring each model, plus flow-graph specific DTOs (`RecipeExecutionEdgeDTO`, `RecipeExecutionStepDTO`, `RecipeFlowGenerationRequestDTO`/`ResponseDTO`, `RecipeFlowSaveRequestDTO`, `RecipeSaveResponseDTO`) and `VisualizationJobResponseDTO`.

### `restclient/`

Outbound HTTP clients to external AI and storage providers, isolated behind small provider-agnostic interfaces so the `ai` package doesn't depend on any single vendor.

- **client** — `AIClient` (chat interface, implemented by `GeminiClient`, `OpenAIClient`, `OllamaClient`), `ImageGenerationClient` (implemented by `GeminiImageClient`, `DrawThingsImageClient`), `ImageStorageClient` (implemented by `SupabaseImageStorageClient`).
- **config** — `AiRestClientConfig` (registers a `RestClient` bean per provider) plus one `@ConfigurationProperties` class per provider: `GeminiProperties`, `OpenAIProperties`, `OllamaProperties`, `DrawThingsProperties`, `SupabaseProperties`.
- **dto** — provider-agnostic `AIRequest`/`AIResponse`.
- **exception** — `AIClientException` (base) and its subtypes `AIAuthenticationException`, `AICommunicationException`, `AIInvalidResponseException`, `AITimeoutException`, mapped from provider HTTP responses (401/403, 429/5xx, malformed body, timeout/unreachable).

### `store/`

The kitchen's master-data/catalog module: ingredient and equipment catalogs, a polymorphic per-user/kitchen `Inventory`, and `ItemCost` price-history records.

- **controller** — `IngredientController` (full CRUD), `EquipmentController` (create/list), `ItemCostController` (record a cost entry).
- **service** — `IIngredientService`/`IngredientServiceImpl`, `IEquipmentService`/`EquipmentServiceImpl`, `IItemCostService`/`ItemCostServiceImpl` — each enforcing unique names on create.
- **repository** — `IngredientRepository`, `EquipmentRepository`, `ItemCostRepository` (`findByItemTypeAndItemIdOrderByEffectiveFromDesc` returns an item's price history newest-first).
- **model** — `Ingredient`, `Equipment`, `Inventory`, `ItemCost`, and the `ItemType` enum (`INGREDIENT`/`EQUIPMENT`) that discriminates the polymorphic references in `Inventory`/`ItemCost`.
- **dto** — request/response/update DTOs for ingredients, equipment, and item cost.

## `common/` — cross-cutting infrastructure

Shared by every feature package above; has no `controller/`.

- **concurrent** — a generic, framework-agnostic task-pool abstraction used to run AI visualization generation concurrently: `Task<R>`/`NamedTask<R>` (a unit of work), `TaskPool`/`ThreadPoolTaskPool` (bounded executor that submits one task or a batch and catches per-task failures into a result rather than propagating), `TaskResult<R>`/`BatchResult<R>` (success/failure outcome carriers), `TaskPoolFactory` (mints and shuts down named pools).
- **config** — `MongoConfig` (Mongo client bean), `JacksonConfig` (shared `ObjectMapper`), `SecurityConfig` (Spring Security filter chain: BCrypt, CORS for the local frontend, CSRF disabled, JWT filter, path-based authorization), `TaskPoolConfig` (registers the `"visualization"` and `"visualization-orchestrator"` `TaskPool` beans, kept separate to avoid deadlock).
- **mapper** — one `*Mapper` class per resource converting entities ↔ DTOs, centralizing conversion logic used by the `service` layers across all feature packages (`UserMapper`, `KitchenMapper`, `OrderMapper`, `InventoryMapper`, `IngredientMapper`, `EquipmentMapper`, `ItemCostMapper`, `KitchenInventoryMapper`, `ProcessTemplateMapper`, `ProcessTemplateStepMapper`, `StepDefinitionMapper`, `ProcessExecutionMapper`, `StepExecutionMapper`, `ProcessEquipmentUsageMapper`, `ProcessIngredientUsageMapper`).
- **exception** — `GlobalExceptionHandler` (`@RestControllerAdvice` mapping every custom/expected exception to an HTTP status + `ErrorResponse` body), and the custom exceptions it handles: `AuthException`, `RecipeAccessDeniedException`, `RecipeFlowGenerationException`, `UserNotFoundException`.
- **tools** — `PresetUp`, a standalone one-off CLI-style tool that idempotently seeds a demo user/kitchen/equipment/ingredients/inventory into MongoDB.
- **utils** — `ApiResponse<T>` (generic API response envelope), `VisualizationKeyBuilder` (builds normalized cache keys for recipe-step visualizations).
- **root** — `DBSequence` (Mongo model for auto-increment counters), `SequenceGeneratorService` (atomic find-and-modify sequence-id generator used by every feature that needs a `Long` id).

## Root application files

- `VirtualKitchenApplication.java` — Spring Boot entry point; starts the application and component-scans all feature packages.
- `MongoVerifyRunner.java` — `CommandLineRunner` that verifies MongoDB connectivity on boot (prints db/collection names, writes a throwaway document).
- `DummyDataLoader.java` — manual demo-data seeding helper; no longer runs automatically at startup — invoke `runLoader(...)` directly or via `common/tools/PresetUp`.

## Example: recipe visualization request flow

- `RecipeVisualizationController` receives a request to visualize a recipe/step.
- It calls `AIRecipeVisualizationService` (or, for async jobs, `VisualizationJobService`), which resolves prompts via `AIVisualizationPromptBuilder` and dispatches to a `restclient` image-generation client (Gemini or DrawThings) plus a storage client (Supabase).
- Results are persisted as `ai/model` documents (`VisualizationAsset`/`VisualizationClip`/`VisualizationJob`) via their repositories.
- Async work is fanned out on the `common/concurrent` `TaskPool` so the HTTP request thread isn't blocked on slow AI calls.
- The controller returns a DTO (e.g. `VisualizationJobResponseDTO`) describing progress/results.

## Summary

- Each feature package (`ai`, `auth`, `kitchen`, `recipe`, `store`) is a self-contained vertical slice with its own controller/service/repository/model/dto layers.
- `restclient` isolates all outbound calls to external AI/storage providers behind provider-agnostic interfaces.
- `common` holds everything cross-cutting: config, a generic concurrency framework, mappers, exception handling, and shared utilities.
- Requests flow `Controller -> Service -> Repository -> MongoDB`, with mapping to/from DTOs happening in the service layer.
