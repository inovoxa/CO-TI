<template>
  <div v-loading="loading">
    <template v-if="p">
      <el-row :gutter="12">
        <el-col :lg="6" :sm="12" :xs="24">
          <ContentWrap>
            <div class="text-13px text-[var(--el-text-color-secondary)]">Agente OCS</div>
            <div class="text-28px font-bold">{{ p.porOrigem.ocs ?? 0 }}</div>
          </ContentWrap>
        </el-col>
        <el-col :lg="6" :sm="12" :xs="24">
          <ContentWrap>
            <div class="text-13px text-[var(--el-text-color-secondary)]">GLPI Agent</div>
            <div class="text-28px font-bold">{{ p.porOrigem.glpi_agent ?? 0 }}</div>
          </ContentWrap>
        </el-col>
        <el-col :lg="6" :sm="12" :xs="24">
          <ContentWrap>
            <div class="text-13px text-[var(--el-text-color-secondary)]"
              >Inventariados nas últimas 24 h</div
            >
            <div class="text-28px font-bold">{{ p.ultimas24h }}</div>
          </ContentWrap>
        </el-col>
        <el-col :lg="6" :sm="12" :xs="24">
          <ContentWrap>
            <div class="text-13px text-[var(--el-text-color-secondary)]">
              Sem comunicação há {{ p.semContatoDias }} dias
            </div>
            <div class="text-28px font-bold">
              <el-link
                :type="p.semContato ? 'danger' : 'default'"
                :underline="false"
                class="!text-28px"
                @click="
                  router.push({ path: '/inventario/ativos', query: { alerta: 'sem_contato' } })
                "
                >{{ p.semContato }}</el-link
              >
            </div>
          </ContentWrap>
        </el-col>
      </el-row>

      <ContentWrap title="Como apontar os agentes">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="Agente OCS (Windows/Linux)">
            <code>{{ base }}/ocsinventory</code>
            <div class="mt-4px text-12px text-[var(--el-text-color-secondary)]">
              Na instalação do agente Windows: <code>/SERVER={{ base }}/ocsinventory</code>
              <template v-if="p.comSenha"> /USER=… /PWD=…</template>. Os agentes existentes
              funcionam sem alteração: basta apontar o endereço (ou o nome do servidor antigo) para
              cá.
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="GLPI Agent">
            <code>{{ base }}/front/inventory.php</code>
            <div class="mt-4px text-12px text-[var(--el-text-color-secondary)]">
              O mesmo caminho do GLPI, então agentes que já apontavam para o GLPI continuam
              funcionando.
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="Redes permitidas">
            <template v-if="p.redes.length">
              <el-tag
                v-for="r in p.redes"
                :key="r"
                class="mr-4px"
                disable-transitions
                size="small"
                >{{ r }}</el-tag
              >
            </template>
            <el-tag v-else disable-transitions size="small" type="warning"
              >Qualquer origem (configure CO_TI_INVENTARIO_REDES)</el-tag
            >
          </el-descriptions-item>
          <el-descriptions-item label="Senha dos agentes">
            {{ p.comSenha ? 'Exigida (HTTP Basic)' : 'Não exigida' }}
          </el-descriptions-item>
          <el-descriptions-item label="Frequência">
            O agente pergunta a cada {{ p.prologoHoras }} h;
            {{
              p.inventarioDias > 0
                ? `manda o inventário completo a cada ${p.inventarioDias} dia(s)`
                : 'manda o inventário completo em todo contato'
            }}.
          </el-descriptions-item>
        </el-descriptions>
      </ContentWrap>

      <el-row :gutter="12">
        <el-col :lg="10" :xs="24">
          <ContentWrap title="Versões em uso">
            <el-table :data="p.versoes" size="small">
              <el-table-column label="Agente" min-width="200" prop="agente" show-overflow-tooltip />
              <el-table-column label="Tipo" width="110">
                <template #default="{ row }">{{ ORIGENS[row.origem] ?? row.origem }}</template>
              </el-table-column>
              <el-table-column align="right" label="Máquinas" prop="total" width="90" />
            </el-table>
          </ContentWrap>
        </el-col>
        <el-col :lg="14" :xs="24">
          <ContentWrap title="Últimos inventários recebidos">
            <el-table
              :data="p.recentes"
              class="cursor-pointer"
              size="small"
              @row-click="(r) => router.push({ path: '/inventario/ativos', query: { id: r.id } })"
            >
              <el-table-column label="Máquina" min-width="160" prop="nome" show-overflow-tooltip />
              <el-table-column label="Usuário" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">{{
                  row.usuarioNome || row.usuarioLogin || '—'
                }}</template>
              </el-table-column>
              <el-table-column label="Recebido" width="140">
                <template #default="{ row }">{{ quando(row.ultimoInventario) }}</template>
              </el-table-column>
            </el-table>
          </ContentWrap>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script lang="ts" setup>
import * as Inv from '@/api/inventario'
import { ORIGENS } from '@/api/inventario'

defineOptions({ name: 'InventarioAgente' })
const router = useRouter()
const loading = ref(true)
const p = ref<Inv.PainelAgentes>()
const base = window.location.origin
const quando = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—'

onMounted(async () => {
  try {
    p.value = await Inv.getPainelAgentes()
  } finally {
    loading.value = false
  }
})
</script>
