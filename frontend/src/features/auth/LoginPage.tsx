export function LoginPage() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-surface-muted px-4">
      <div className="bg-white rounded-2xl shadow-lg p-8 w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-gray-900 mb-2">ShopMate</h1>
          <p className="text-gray-500">Share shopping lists with anyone</p>
        </div>
        <a
          href="/oauth2/authorization/google"
          className="flex items-center justify-center gap-3 w-full px-4 py-3 border border-surface-border rounded-xl font-medium text-gray-700 hover:bg-surface-muted transition-colors"
        >
          <GoogleIcon />
          Sign in with Google
        </a>
      </div>
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
