/// <reference types="vite/client" />

// Vite inline worker imports — returns a constructor that creates a Worker
declare module '*?worker&inline' {
  const WorkerConstructor: { new (): Worker }
  export default WorkerConstructor
}
