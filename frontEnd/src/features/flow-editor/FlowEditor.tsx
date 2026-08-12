import FlowCanvas from './components/canvas/FlowCanvas'
import './styles/flow-editor.css'

interface FlowEditorProps {
  recipeId: number
  recipeTitle?: string
  onBackToRecipes?: () => void
}

export default function FlowEditor({
  recipeId,
  recipeTitle,
  onBackToRecipes,
}: FlowEditorProps) {
  const safeRecipeTitle =
    recipeTitle?.trim() ||
    `Recipe ${recipeId}`

  const handleBack = () => {
    onBackToRecipes?.()
  }

  return (
    <div className="h-full w-full">
      <FlowCanvas
        recipe={{ id: recipeId, title: safeRecipeTitle }}
        onBack={handleBack}
      />
    </div>
  )
}