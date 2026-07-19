export function LoginPage() {
  return (
    <div className="min-h-screen bg-marigold flex items-center justify-center px-6 py-12">
      <div className="w-full max-w-sm">
        <header className="text-center mb-8">
          <h1 className="text-display font-bold text-ink tracking-tight">ShopMate</h1>
          <p className="mt-2 text-item font-medium text-honey-deep">
            One list for the whole household
          </p>
        </header>

        {/* A glimpse of the product: the shared pad, mid-shop */}
        <div
          aria-hidden="true"
          className="bg-panel rounded-2xl border border-marigold-deep/30 shadow-[0_12px_32px_-16px_oklch(0.40_0.08_68/0.45)] px-5 py-2 mb-8"
        >
          <PreviewRow name="Milk" checked />
          <PreviewRow name="Eggs" checked />
          <PreviewRow name="Basil" quantity="2 bunches" />
          <PreviewRow name="Olive oil" last />
        </div>

        <a
          href="/oauth2/authorization/google"
          className="pressable flex items-center justify-center gap-3 w-full min-h-touch px-4 py-3 bg-ink text-panel rounded-full text-body font-semibold shadow-sm hover:bg-ink/90 focus-visible:outline-ink"
        >
          <GoogleIcon />
          Sign in with Google
        </a>

        <p className="mt-4 text-center text-label text-honey-deep">
          Everyone edits together — changes appear instantly.
        </p>
      </div>
    </div>
  )
}

function PreviewRow({
  name,
  quantity,
  checked = false,
  last = false,
}: {
  name: string
  quantity?: string
  checked?: boolean
  last?: boolean
}) {
  return (
    <div
      className={`flex items-center gap-3 py-2.5 ${last ? '' : 'border-b border-line'}`}
    >
      <span
        className={`h-5 w-5 rounded-full border-2 flex items-center justify-center ${
          checked ? 'bg-marigold border-marigold' : 'border-line'
        }`}
      >
        {checked && (
          <svg className="w-3 h-3" viewBox="0 0 12 12" fill="none">
            <path
              d="M2.5 6.5l2.5 2.5 4.5-5.5"
              stroke="oklch(0.27 0.025 65)"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        )}
      </span>
      <span
        className={`text-body ${
          checked ? 'text-ink-mute line-through decoration-ink-mute/60' : 'text-ink'
        }`}
      >
        {name}
      </span>
      {quantity && (
        <span className="ml-auto text-label font-semibold text-honey-deep bg-marigold-faint rounded-full px-2 py-0.5">
          {quantity}
        </span>
      )}
    </div>
  )
}

function GoogleIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 48 48"
      width="20"
      height="20"
      aria-hidden="true"
    >
      <path
        fill="#4285F4"
        d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v8.51h12.93c-.49 2.81-2.12 5.17-4.56 6.77v5.52h7.29c4.28-3.94 6.32-9.76 6.32-16.25z"
      />
      <path
        fill="#34A853"
        d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.29-5.52c-2.13 1.43-4.87 2.27-8.6 2.27-6.61 0-12.21-4.47-14.21-10.48H2.29v5.7C6.23 42.62 14.49 48 24 48z"
      />
      <path
        fill="#FBBC05"
        d="M9.79 28.46A14.7 14.7 0 0 1 9 24c0-1.56.27-3.07.79-4.46v-5.7H2.29A23.93 23.93 0 0 0 0 24c0 3.87.93 7.52 2.29 10.16l7.5-5.7z"
      />
      <path
        fill="#EA4335"
        d="M24 9.52c3.72 0 7.06 1.28 9.69 3.79l7.21-7.21C36.92 2.09 31.47 0 24 0 14.49 0 6.23 5.38 2.29 13.84l7.5 5.7C11.79 13.99 17.39 9.52 24 9.52z"
      />
    </svg>
  )
}
