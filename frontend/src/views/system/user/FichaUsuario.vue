<!-- Ficha da pessoa: perfis e o que liberam, AD, segundo fator, sessões, acessos, equipamentos, chamados e auditoria. -->
<template>
  <el-drawer v-model="visivel" :size="largura" destroy-on-close>
    <template #header>
      <div v-if="f" class="min-w-0">
        <div class="truncate text-17px font-bold">{{ u!.nickname }}</div>
        <div class="mt-4px flex flex-wrap items-center gap-6px text-13px">
          <span class="text-[var(--el-text-color-secondary)]">{{ u!.username }}</span>
          <el-tag :type="u!.status === 0 ? 'success' : 'info'" disable-transitions size="small">
            {{ u!.status === 0 ? 'Ativo' : 'Inativo' }}
          </el-tag>
          <el-tag
            v-if="u!.origem === 'ad'"
            :type="u!.adAtivo === false ? 'danger' : 'success'"
            disable-transitions
            size="small"
          >
            {{ u!.adAtivo === false ? 'Desativado no AD' : 'AD' }}
          </el-tag>
          <el-tag v-else disable-transitions size="small" type="info">Conta local</el-tag>
          <el-tag v-if="u!.privilegiado" disable-transitions size="small" type="danger"
            >Acesso administrativo</el-tag
          >
        </div>
      </div>
    </template>
    <div v-loading="carregando">
      <template v-if="f">
        <div class="mb-12px flex flex-wrap gap-8px">
          <el-button v-hasPermi="['system:user:update']" @click="emit('editar', u!.id)">
            <Icon class="mr-4px" icon="ep:edit" /> Editar
          </el-button>
          <el-button
            v-if="f.sessoes.length"
            v-hasPermi="['system:user:update']"
            plain
            type="warning"
            @click="encerrar"
          >
            <Icon class="mr-4px" icon="ep:switch-button" /> Encerrar
            {{ f.sessoes.length }} sessão(ões)
          </el-button>
        </div>

        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="E-mail">{{ u!.email || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Celular">{{ u!.mobile || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Unidade">
            {{ u!.unidadeNome || '—' }}
            <div v-if="u!.unidadeMaquina" class="ajuda">
              {{
                u!.unidadeOrigem === 'fora'
                  ? 'IP fora das faixas'
                  : u!.unidadeOrigem === 'manual'
                    ? 'dada à mão na máquina'
                    : 'pela faixa de IP'
              }}
              · {{ u!.unidadeMaquina }} {{ u!.unidadeIp }}
            </div>
            <div v-else class="ajuda">sem máquina no inventário</div>
          </el-descriptions-item>
          <el-descriptions-item label="Departamento">{{ u!.deptNome || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Último acesso">
            {{ quando(u!.ultimoAcesso)
            }}<span v-if="u!.ultimoIp" class="ajuda"> · {{ u!.ultimoIp }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="Verificação em duas etapas">
            <el-tag :type="u!.mfa ? 'success' : 'warning'" disable-transitions size="small">
              {{ u!.mfa ? 'Ligada' : 'Desligada' }}
            </el-tag>
            <span v-if="f.mfaAtivadoEm" class="ajuda"> desde {{ quando(f.mfaAtivadoEm) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="Criado em">{{ quando(u!.criadoEm) }}</el-descriptions-item>
          <el-descriptions-item v-if="u!.origem === 'ad'" :span="2" label="Conta do AD">
            <div>{{ f.adLogin }} · sincronizada em {{ quando(u!.adSincronizacao) }}</div>
            <div class="ajuda break-all">{{ f.adDn }}</div>
            <div v-if="f.adGestorDn" class="ajuda break-all">Gestor: {{ f.adGestorDn }}</div>
          </el-descriptions-item>
          <el-descriptions-item v-if="f.observacao" :span="2" label="Observação">{{
            f.observacao
          }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="aba" class="mt-12px">
          <el-tab-pane :label="`Perfis (${u!.perfis.length})`" name="perfis">
            <el-table :data="u!.perfis" size="small">
              <el-table-column label="Perfil" min-width="180" prop="nome" />
              <el-table-column label="Código" prop="codigo" width="150" />
              <el-table-column label="Enxerga" min-width="180" prop="escopo" />
            </el-table>
            <el-empty
              v-if="!u!.perfis.length"
              :image-size="50"
              description="Sem perfil: a pessoa não vê nenhum menu"
            />
          </el-tab-pane>
          <el-tab-pane :label="`Acessos (${f.acessos.length})`" name="acessos">
            <el-table :data="f.acessos" size="small">
              <el-table-column label="Quando" width="150">
                <template #default="{ row }">{{ quando(row.quando) }}</template>
              </el-table-column>
              <el-table-column label="" width="110">
                <template #default="{ row }">
                  <el-tag
                    :type="row.resultado === 0 ? 'success' : 'danger'"
                    disable-transitions
                    size="small"
                  >
                    {{ row.resultado === 0 ? row.tipo : 'recusado' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="IP" prop="ip" width="130" />
              <el-table-column
                label="Navegador"
                min-width="200"
                prop="navegador"
                show-overflow-tooltip
              />
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`Sessões abertas (${f.sessoes.length})`" name="sessoes">
            <el-table :data="f.sessoes" size="small">
              <el-table-column label="Aberta em" width="160">
                <template #default="{ row }">{{ quando(row.criada) }}</template>
              </el-table-column>
              <el-table-column label="Expira em" width="160">
                <template #default="{ row }">{{ quando(row.expira) }}</template>
              </el-table-column>
              <el-table-column label="Aplicativo" prop="cliente" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane
            v-if="checkPermi(['inventario:ativo:query'])"
            :label="`Equipamentos (${ativos.length})`"
            name="ativos"
          >
            <el-table
              :data="ativos"
              class="cursor-pointer"
              size="small"
              @row-click="(r) => ir('/inventario/ativos', r.id)"
            >
              <el-table-column label="Equipamento" min-width="160" prop="nome" />
              <el-table-column
                label="Sistema"
                min-width="160"
                prop="soNome"
                show-overflow-tooltip
              />
              <el-table-column label="IP" prop="ip" width="130" />
              <el-table-column label="Último contato" width="150">
                <template #default="{ row }">{{ quando(row.ultimoContato) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane
            v-if="checkPermi(['itsm:chamado:query'])"
            :label="`Chamados (${chamados.length})`"
            name="chamados"
          >
            <el-table
              :data="chamados"
              class="cursor-pointer"
              size="small"
              @row-click="(r) => ir('/servicedesk/chamados', r.id)"
            >
              <el-table-column label="Nº" width="80">
                <template #default="{ row }">#{{ row.id }}</template>
              </el-table-column>
              <el-table-column label="Título" min-width="220" prop="titulo" show-overflow-tooltip />
              <el-table-column label="Situação" prop="statusRotulo" width="130" />
            </el-table>
            <div class="ajuda mt-6px">Os 20 mais recentes que a pessoa pediu.</div>
          </el-tab-pane>
          <el-tab-pane :label="`O que fez (${f.auditoria.length})`" name="auditoria">
            <el-table :data="f.auditoria" size="small">
              <el-table-column label="Quando" width="150">
                <template #default="{ row }">{{ quando(row.momento) }}</template>
              </el-table-column>
              <el-table-column label="Ação" min-width="190" prop="acao" />
              <el-table-column
                label="Detalhes"
                min-width="200"
                prop="detalhes"
                show-overflow-tooltip
              />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>
  </el-drawer>
</template>

<script lang="ts" setup>
import * as IdentidadeApi from '@/api/identidade'
import * as Inv from '@/api/inventario'
import * as Itsm from '@/api/itsm'
import { checkPermi } from '@/utils/permission'

const emit = defineEmits<{ editar: [id: number]; mudou: [] }>()
const message = useMessage()
const router = useRouter()
const visivel = ref(false)
const carregando = ref(false)
const aba = ref('perfis')
const f = ref<IdentidadeApi.UsuarioFicha>()
const u = computed(() => f.value?.usuario)
const ativos = ref<Inv.Ativo[]>([])
const chamados = ref<any[]>([])
const largura = computed(() => (window.innerWidth < 1100 ? '100%' : '58%'))

const quando = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : 'nunca'
const ir = (path: string, id: number) => {
  visivel.value = false
  router.push({ path, query: { id } })
}

const abrir = async (id: number) => {
  visivel.value = true
  aba.value = 'perfis'
  carregando.value = true
  ativos.value = []
  chamados.value = []
  try {
    f.value = await IdentidadeApi.getFichaUsuario(id)
  } finally {
    carregando.value = false
  }
  if (checkPermi(['inventario:ativo:query']))
    Inv.getAtivosDoUsuario(id)
      .then((l) => (ativos.value = l))
      .catch(() => {})
  if (checkPermi(['itsm:chamado:query']))
    Itsm.getChamadoPage({ fila: 'todos', solicitanteId: id, pageNo: 1, pageSize: 20 })
      .then((p: any) => (chamados.value = p.list))
      .catch(() => {})
}

const encerrar = async () => {
  try {
    await message.confirm(
      `Encerrar as sessões abertas de "${u.value!.username}"? A pessoa precisará entrar de novo.`
    )
  } catch {
    return
  }
  await IdentidadeApi.loteUsuarios({ acao: 'encerrar', ids: [u.value!.id] })
  message.success('Sessões encerradas')
  await abrir(u.value!.id)
  emit('mudou')
}

defineExpose({ abrir })
</script>

<style lang="scss" scoped>
.ajuda {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
