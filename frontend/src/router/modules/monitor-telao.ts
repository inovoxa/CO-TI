/**
 * Rota do telão em tela cheia (sem menu lateral, sem abas).
 *
 * No yudao-ui-admin-vue3 as rotas fixas ficam em src/router/modules/remaining.ts.
 * Abra esse arquivo e acrescente o objeto abaixo ao array exportado (remainingRouter),
 * no mesmo nível de rotas como '/login'. Exemplo:
 *
 *   import { telaoRoute } from './monitor-telao'
 *   const remainingRouter: AppRouteRecordRaw[] = [
 *     ...,
 *     telaoRoute
 *   ]
 *
 * A rota continua exigindo login (o guarda de rotas do projeto vale para ela). Os dados
 * exibidos dependem das permissões do usuário logado: crie um perfil "Telão" só com
 * system:monitor-screen:query, :links:query e :cameras:query, e um usuário para a TV.
 */
export const telaoRoute = {
  path: '/telao',
  name: 'MonitorTelao',
  component: () => import('@/views/monitor/telao/index.vue'),
  meta: {
    hidden: true,
    title: 'Telão',
    noTagsView: true
  }
}
