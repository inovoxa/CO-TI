<!-- Tela de início do CO-TI: pendências do usuário, situação dos módulos que o perfil enxerga e atalhos -->
<template>
  <div class="inicio">
    <el-card shadow="never" class="boas-vindas">
      <div class="bv">
        <el-avatar :src="avatar" :size="56">
          <img src="@/assets/imgs/avatar-padrao.svg" alt="" />
        </el-avatar>
        <div class="bv__texto">
          <h2>{{ saudacao }}, {{ username }}</h2>
          <p>{{ hoje }} · Centro de Operações de TI</p>
        </div>
        <div class="bv__acoes">
          <el-button v-if="tem('AssistenteInterno')" @click="ir('AssistenteInterno')">
            <Icon icon="ep:magic-stick" class="mr-6px" /> Perguntar ao assistente
          </el-button>
          <el-button v-if="tem('ItsmCatalogo')" type="primary" @click="ir('ItsmCatalogo')">
            <Icon icon="ep:plus" class="mr-6px" /> Abrir chamado
          </el-button>
        </div>
      </div>
    </el-card>

    <el-skeleton :loading="loading" animated :rows="3" class="mt-12px">
      <section v-for="g in grupos" :key="g.titulo" class="grupo">
        <h3>{{ g.titulo }}</h3>
        <div class="grid-kpi">
          <Kpi v-for="k in g.kpis" :key="k.label" v-bind="k" />
        </div>
      </section>
    </el-skeleton>

    <div class="cols">
      <div class="col">
        <el-card shadow="never" :body-style="{ padding: '0' }">
          <template #header>
            <div class="ch">
              Meus chamados <small>{{ meusAbertos }} em aberto</small>
              <el-link
                v-if="tem('ItsmPortal')"
                type="primary"
                :underline="false"
                @click="ir('ItsmPortal')"
              >
                Ver todos
              </el-link>
            </div>
          </template>
          <ul class="lista">
            <li v-for="c in meus" :key="c.id" @click="ir('ItsmPortal', { chamado: c.id })">
              <span class="lista__id">#{{ c.id }}</span>
              <div class="lista__corpo">
                <p>{{ c.titulo }}</p>
                <small
                  >{{ c.categoria || 'Sem categoria' }} · aberto em
                  {{ formatTime(c.abertoEm, 'dd/MM/yyyy HH:mm') }}</small
                >
              </div>
              <el-tag :type="corStatus(c.status)" size="small">{{ c.statusRotulo }}</el-tag>
            </li>
            <li v-if="!meus.length" class="vazio">
              Você não tem chamados.
              <el-link
                v-if="tem('ItsmCatalogo')"
                type="primary"
                :underline="false"
                @click="ir('ItsmCatalogo')"
              >
                Abrir pelo catálogo de serviços
              </el-link>
            </li>
          </ul>
        </el-card>

        <el-card
          v-if="aprovacoes.length"
          shadow="never"
          class="mt-12px"
          :body-style="{ padding: '0' }"
        >
          <template #header>
            <div class="ch">
              Aguardando sua aprovação <small>{{ aprovacoes.length }}</small>
              <el-link type="primary" :underline="false" @click="ir('ItsmAprovacao')"
                >Responder</el-link
              >
            </div>
          </template>
          <ul class="lista">
            <li v-for="a in aprovacoes.slice(0, 5)" :key="a.id" @click="ir('ItsmAprovacao')">
              <span class="lista__id">#{{ a.chamadoId }}</span>
              <div class="lista__corpo">
                <p>{{ a.chamadoTitulo }}</p>
                <small
                  >{{ a.solicitante || a.solicitadoPor }} · pedido em
                  {{ formatTime(a.pedidoEm, 'dd/MM/yyyy HH:mm') }}</small
                >
              </div>
            </li>
          </ul>
        </el-card>
      </div>

      <div class="col">
        <el-card shadow="never">
          <template #header><div class="ch">Acesso rápido</div></template>
          <div class="atalhos">
            <button
              v-for="a in atalhos"
              :key="a.rota"
              type="button"
              class="atalho"
              @click="ir(a.rota)"
            >
              <Icon :icon="a.icone" :size="22" :style="{ color: a.cor }" />
              <span>{{ a.nome }}</span>
            </button>
          </div>
        </el-card>

        <el-card
          v-if="eventos.length"
          shadow="never"
          class="mt-12px"
          :body-style="{ padding: '0' }"
        >
          <template #header>
            <div class="ch">
              Monitoramento <small>últimas mudanças de estado</small>
              <el-link type="primary" :underline="false" @click="ir('MonitoramentoMonitor')"
                >Monitores</el-link
              >
            </div>
          </template>
          <ol class="feed">
            <li v-for="(e, i) in eventos" :key="e.monitorId + '-' + i" :class="`e-${e.estado}`">
              <time>{{ formatTime(e.momento, 'dd/MM HH:mm') }}</time>
              <div>
                <span class="m">{{ ESTADOS[e.estado]?.rotulo }}</span>
                <p
                  >{{ e.monitorNome }}<template v-if="e.msg"> · {{ e.msg }}</template></p
                >
              </div>
            </li>
          </ol>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { formatTime } from '@/utils'
import { checkPermi } from '@/utils/permission'
import { useUserStore } from '@/store/modules/user'
import { useRouter } from 'vue-router'
import Kpi from '@/views/monitor/components/Kpi.vue'
import * as Itsm from '@/api/itsm'
import * as Monitoramento from '@/api/monitoramento'
import * as Inventario from '@/api/inventario'

defineOptions({ name: 'Index' })

type KpiItem = InstanceType<typeof Kpi>['$props']

const router = useRouter()
const userStore = useUserStore()
const avatar = userStore.getUser.avatar
const username = userStore.getUser.nickname
const { corStatus } = Itsm
const { ESTADOS } = Monitoramento

const agora = new Date()
const h = agora.getHours()
const saudacao = h < 12 ? 'Bom dia' : h < 18 ? 'Boa tarde' : 'Boa noite'
const hoje = agora.toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' })

/** os menus vêm do perfil: só existe rota para o que o usuário pode abrir */
const tem = (rota: string) => router.hasRoute(rota)
const ir = (rota: string, query?: Record<string, any>) => router.push({ name: rota, query })
const link = (rota: string, query?: Record<string, any>) =>
  tem(rota) ? { name: rota, query } : undefined
const n = (v?: number) => (v ?? 0).toLocaleString('pt-BR')

const ATALHOS = [
  { rota: 'ItsmCatalogo', nome: 'Catálogo de serviços', icone: 'ep:grid', cor: '#409eff' },
  { rota: 'ItsmPortal', nome: 'Meus chamados', icone: 'ep:chat-line-square', cor: '#409eff' },
  { rota: 'ItsmKb', nome: 'Base de conhecimento', icone: 'ep:reading', cor: '#67c23a' },
  { rota: 'ItsmChamado', nome: 'Fila de chamados', icone: 'ep:tickets', cor: '#e6a23c' },
  { rota: 'ItsmPainel', nome: 'Painel do Service Desk', icone: 'ep:data-analysis', cor: '#e6a23c' },
  { rota: 'ItsmExecucaoAd', nome: 'Execuções no AD', icone: 'ep:cpu', cor: '#e6a23c' },
  { rota: 'AtendimentoConversa', nome: 'Conversas', icone: 'ep:chat-line-round', cor: '#25d366' },
  { rota: 'InventarioAtivo', nome: 'Inventário', icone: 'ep:monitor', cor: '#8e44ad' },
  { rota: 'MonitoramentoMonitor', nome: 'Monitoramento', icone: 'ep:odometer', cor: '#f56c6c' },
  {
    rota: 'MonitorVisaoGeral',
    nome: 'Centro de Operações',
    icone: 'ep:data-board',
    cor: '#f56c6c'
  },
  { rota: 'AssistenteInterno', nome: 'Assistente IA', icone: 'ep:magic-stick', cor: '#7c3aed' }
]
const atalhos = ATALHOS.filter((a) => tem(a.rota))

const loading = ref(true)
const meus = ref<Itsm.Chamado[]>([])
const meusAbertos = ref(0)
const aprovacoes = ref<Itsm.Aprovacao[]>([])
const fila = ref<Record<string, number>>()
const monitores = ref<Monitoramento.Contagens>()
const ativos = ref<Record<string, number>>()
const eventos = ref<Monitoramento.Evento[]>([])

const grupos = computed(() => {
  const g: { titulo: string; kpis: KpiItem[] }[] = []
  const pendencias: KpiItem[] = [
    {
      label: 'Meus chamados abertos',
      valor: n(meusAbertos.value),
      apoio: meusAbertos.value ? 'acompanhe as respostas' : 'nada em aberto',
      to: link('ItsmPortal')
    }
  ]
  if (tem('ItsmAprovacao')) {
    pendencias.push({
      label: 'Aprovações aguardando',
      valor: n(aprovacoes.value.length),
      apoio: aprovacoes.value.length ? 'pedidos esperando você' : 'nenhuma pendente',
      nivel: aprovacoes.value.length ? 'warn' : '',
      to: link('ItsmAprovacao')
    })
  }
  g.push({ titulo: 'Para você', kpis: pendencias })

  const f = fila.value
  if (f) {
    g.push({
      titulo: 'Service Desk',
      kpis: [
        {
          label: 'Chamados abertos',
          valor: n(f.abertos),
          apoio: `${n(f.pendentes)} pendentes`,
          to: link('ItsmChamado')
        },
        {
          label: 'Novos sem técnico',
          valor: n(f.novos),
          nivel: f.novos ? 'warn' : 'ok',
          to: link('ItsmChamado')
        },
        {
          label: 'SLA vencido',
          valor: n(f.sla),
          apoio: f.sla ? 'priorize estes' : 'todos no prazo',
          nivel: f.sla ? 'crit' : 'ok',
          to: link('ItsmChamado')
        },
        {
          label: 'Solucionados',
          valor: n(f.solucionados),
          apoio: 'aguardando fechamento',
          to: link('ItsmPainel')
        }
      ]
    })
  }

  const m = monitores.value
  if (m) {
    g.push({
      titulo: 'Monitoramento',
      kpis: [
        {
          label: 'No ar',
          valor: `${n(m.noAr)}/${n(m.todos - m.pausado)}`,
          apoio: m.lento ? `${n(m.lento)} lentos` : 'tempo de resposta normal',
          nivel: m.fora ? 'crit' : m.lento ? 'warn' : 'ok',
          to: link('MonitoramentoMonitor')
        },
        {
          label: 'Fora do ar',
          valor: n(m.fora),
          apoio: m.manutencao ? `${n(m.manutencao)} em manutenção` : '',
          nivel: m.fora ? 'crit' : 'ok',
          to: link('MonitoramentoMonitor')
        },
        {
          label: 'Certificados vencendo',
          valor: n(m.certificadoVencendo),
          nivel: m.certificadoVencendo ? 'warn' : '',
          to: link('MonitoramentoMonitor')
        }
      ]
    })
  }

  const a = ativos.value
  if (a) {
    g.push({
      titulo: 'Inventário',
      kpis: [
        { label: 'Ativos', valor: n(a.todos), to: link('InventarioAtivo') },
        {
          label: 'Sem contato',
          valor: n(a.sem_contato),
          nivel: a.sem_contato ? 'warn' : '',
          to: link('InventarioAtivo')
        },
        {
          label: 'Antivírus com problema',
          valor: n(a.antivirus),
          nivel: a.antivirus ? 'crit' : 'ok',
          to: link('InventarioAtivo')
        },
        {
          label: 'SO sem suporte',
          valor: n(a.sem_suporte),
          nivel: a.sem_suporte ? 'warn' : '',
          to: link('InventarioAtivo')
        }
      ]
    })
  }
  return g
})

/** cada bloco carrega sozinho: a falha de um módulo não derruba a tela */
const carregar = async () => {
  await Promise.allSettled([
    Itsm.getMeus({ pageNo: 1, pageSize: 5, somenteAbertos: false }).then((p) => {
      meus.value = p.list
    }),
    Itsm.getMeus({ pageNo: 1, pageSize: 1, somenteAbertos: true }).then((p) => {
      meusAbertos.value = p.total
    }),
    tem('ItsmAprovacao') &&
      Itsm.getMinhasAprovacoes(true).then((l) => {
        aprovacoes.value = l
      }),
    checkPermi(['itsm:chamado:query']) &&
      Itsm.getContagens().then((c) => {
        fila.value = c
      }),
    checkPermi(['monitoramento:monitor:query']) &&
      Promise.all([Monitoramento.getContagens(), Monitoramento.getEventos(2)]).then(([c, e]) => {
        monitores.value = c
        eventos.value = e.slice(0, 8)
      }),
    checkPermi(['inventario:ativo:query']) &&
      Inventario.getContagens().then((c) => {
        ativos.value = c
      })
  ])
  loading.value = false
}

carregar()
</script>

<style scoped lang="scss">
.inicio {
  padding: 4px;
}

.bv {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;

  h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
  }

  p {
    margin: 4px 0 0;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}

.bv__texto {
  flex: 1;
  min-width: 200px;
}

.bv__acoes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.grupo {
  margin-top: 14px;

  h3 {
    margin: 0 0 8px;
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.06em;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
  }
}

.grid-kpi {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.cols {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
  gap: 12px;
  margin-top: 16px;
}

.col {
  min-width: 0;
}

.ch {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
  font-weight: 600;

  small {
    font-weight: 400;
    color: var(--el-text-color-secondary);
  }

  .el-link {
    margin-left: auto;
    font-weight: 400;
  }
}

.lista {
  padding: 0;
  margin: 0;
  list-style: none;

  li {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 16px;
    cursor: pointer;
    border-bottom: 1px solid var(--el-border-color-lighter);

    &:last-child {
      border-bottom: 0;
    }

    &:hover {
      background: var(--el-fill-color-light);
    }
  }

  li.vazio {
    display: block;
    color: var(--el-text-color-secondary);
    cursor: default;

    &:hover {
      background: none;
    }
  }

  p {
    margin: 0;
    overflow: hidden;
    font-size: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.lista__id {
  flex: none;
  width: 56px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
}

.lista__corpo {
  flex: 1;
  min-width: 0;
}

.atalhos {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.atalho {
  display: flex;
  padding: 12px 6px;
  font-size: 12px;
  color: var(--el-text-color-primary);
  text-align: center;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  flex-direction: column;
  align-items: center;
  gap: 6px;

  &:hover {
    background: var(--el-fill-color-light);
    border-color: var(--el-color-primary-light-5);
  }
}

.feed {
  padding: 0;
  margin: 0;
  list-style: none;

  li {
    display: grid;
    grid-template-columns: 76px minmax(0, 1fr);
    gap: 8px;
    padding: 7px 12px;
    border-left: 3px solid var(--el-border-color-light);
  }

  li.e-0 {
    border-left-color: var(--el-color-danger);
  }

  li.e-1 {
    border-left-color: var(--el-color-success);
  }

  li.e-2 {
    border-left-color: var(--el-color-warning);
  }

  time {
    font-size: 12px;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-secondary);
  }

  .m {
    font-size: 11px;
    letter-spacing: 0.04em;
    color: var(--el-text-color-secondary);
    text-transform: uppercase;
  }

  p {
    margin: 1px 0 0;
    font-size: 13px;
  }
}

@media (width <= 1100px) {
  .grid-kpi {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cols {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
