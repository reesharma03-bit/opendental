import { AlertDialog, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from '@/components/ui/alert-dialog';

interface Props { singular: string; onCancel: () => void; onConfirm: () => void; }

export default function DeletePreviewDialog({ singular, onCancel, onConfirm }: Props) {
  return (
    <AlertDialog open onOpenChange={(o) => !o && onCancel()}>
      <AlertDialogContent className="rounded-2xl">
        <AlertDialogHeader>
          <AlertDialogTitle className="font-[Manrope] font-extrabold">Remove this preview {singular.toLowerCase()}?</AlertDialogTitle>
          <AlertDialogDescription>This removes the record from the in-memory preview list only. Nothing is deleted in Open Dental, because this screen is not connected to it.</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Keep record</AlertDialogCancel>
          <button type="button" onClick={onConfirm} data-testid="button-preview-confirm-delete" className="inline-flex h-9 items-center justify-center rounded-md bg-rose-600 px-4 text-sm font-semibold text-white hover:bg-rose-700">Delete preview record</button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
