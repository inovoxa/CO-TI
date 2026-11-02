// Service worker do CO-TI: deixa o portal instalável e abre mais rápido.
//
// Guarda só arquivos estáticos do portal. Nunca toca em dados: a API, o WebSocket, os anexos e os caminhos dos agentes
// vão direto para a rede, então nada de chamado, pessoa ou senha fica no aparelho. Sem rede, mostra a página offline.
const VERSAO = 'coti-v1'
const OFFLINE = '/offline.html'
const INICIAIS = [OFFLINE, '/favicon.svg', '/pwa/icone-192.png']

self.addEventListener('install', (evento) => {
  evento.waitUntil(
    caches
      .open(VERSAO)
      .then((cache) => cache.addAll(INICIAIS))
      .then(() => self.skipWaiting())
  )
})

self.addEventListener('activate', (evento) => {
  evento.waitUntil(
    caches
      .keys()
      .then((nomes) => Promise.all(nomes.filter((n) => n !== VERSAO).map((n) => caches.delete(n))))
      .then(() => self.clients.claim())
  )
})

self.addEventListener('fetch', (evento) => {
  const pedido = evento.request
  if (pedido.method !== 'GET') return
  const url = new URL(pedido.url)
  if (url.origin !== self.location.origin) return

  // abrir uma página: sempre a versão nova do servidor; sem rede, a página offline
  if (pedido.mode === 'navigate') {
    evento.respondWith(fetch(pedido).catch(() => caches.match(OFFLINE)))
    return
  }

  // /assets/ tem o hash do conteúdo no nome: o mesmo nome é sempre o mesmo arquivo
  if (url.pathname.startsWith('/assets/')) {
    evento.respondWith(
      caches.match(pedido).then(
        (guardado) =>
          guardado ||
          fetch(pedido).then((resposta) => {
            if (resposta.ok) {
              const copia = resposta.clone()
              caches.open(VERSAO).then((cache) => cache.put(pedido, copia))
            }
            return resposta
          })
      )
    )
  }
})
