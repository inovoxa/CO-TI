<template>
  <div>
      <el-tabs v-model="aba" class="abas">
        <el-tab-pane label="Usuários" name="usuarios" />
        <el-tab-pane label="Revisão de acessos" name="revisao" />
      </el-tabs>

      <RevisaoAcessos v-if="aba === 'revisao'" @abrir="(id) => fichaRef?.abrir(id)" />

      <template v-else>
        <ContentWrap>
          <el-form :inline="true" class="-mb-15px" label-width="0">
            <el-form-item>
              <el-input
                v-model="q.busca"
                class="!w-260px"
                clearable
                placeholder="Nome, usuário, e-mail ou celular"
                @keyup.enter="buscar"
              >
                <template #prefix><Icon icon="ep:search" /></template>
              </el-input>
            </el-form-item>
            <el-form-item>
              <el-tree-select
                v-model="q.unidadeId"
                :data="unidades"
                :props="{ label: 'name', children: 'children' }"
                :render-after-expand="false"
                check-strictly
                class="!w-230px"
                clearable
                filterable
                node-key="id"
                placeholder="Unidade (com as subunidades)"
                @change="buscar"
              />
            </el-form-item>
            <el-form-item>
              <el-select
                v-model="q.status"
                class="!w-120px"
                clearable
                placeholder="Situação"
                @change="buscar"
              >
                <el-option :value="0" label="Ativos" />
                <el-option :value="1" label="Inativos" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-select
                v-model="q.roleId"
                class="!w-180px"
                clearable
                filterable
                placeholder="Perfil"
                @change="buscar"
              >
                <el-option v-for="r in perfis" :key="r.id" :label="r.name" :value="r.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-select
                v-model="q.origem"
                class="!w-130px"
                clearable
                placeholder="Origem"
                @change="buscar"
              >
                <el-option label="AD" value="ad" />
                <el-option label="Conta local" value="local" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-select
                v-model="q.semAcessoDias"
                class="!w-170px"
                clearable
                placeholder="Sem acesso há"
                @change="buscar"
              >
                <el-option
                  v-for="d in [30, 60, 90, 180, 365]"
                  :key="d"
                  :label="`Sem acesso há ${d} dias`"
                  :value="d"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="q.semMfa" @change="buscar"
                >Sem verificação em duas etapas</el-checkbox
              >
              <el-checkbox v-model="q.privilegiado" @change="buscar"
                >Com acesso administrativo</el-checkbox
              >
            </el-form-item>
            <el-form-item>
              <el-button @click="buscar"><Icon icon="ep:search" class="mr-4px" />Buscar</el-button>
              <el-button @click="limpar"><Icon icon="ep:refresh" class="mr-4px" />Limpar</el-button>
              <el-button
                v-hasPermi="['system:user:create']"
                plain
                type="primary"
                @click="openForm('create')"
              >
                <Icon icon="ep:plus" class="mr-4px" />Novo
              </el-button>
              <el-button v-hasPermi="['system:user:import']" plain @click="importador?.abrir()">
                <Icon icon="ep:upload" class="mr-4px" />Importar
              </el-button>
              <el-button
                v-hasPermi="['system:user:export']"
                :loading="exportando"
                plain
                @click="exportar"
              >
                <Icon icon="ep:download" class="mr-4px" />Exportar
              </el-button>
            </el-form-item>
          </el-form>
        </ContentWrap>

        <ContentWrap>
          <div v-if="selecionados.length" class="lote">
            <span>
              <b>{{ selecionados.length }}</b> selecionado(s)
            </span>
            <el-dropdown trigger="click" @command="executar">
              <el-button size="small" type="primary">
                Executar ação <Icon class="ml-4px" icon="ep:arrow-down" />
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="checkPermi(['system:user:update'])" command="ativar"
                    >Ativar</el-dropdown-item
                  >
                  <el-dropdown-item v-if="checkPermi(['system:user:update'])" command="desativar">
                    Desativar (encerra as sessões)
                  </el-dropdown-item>
                  <el-dropdown-item v-if="checkPermi(['system:user:update'])" command="mover">
                    Mover de departamento…
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="checkPermi(['system:permission:assign-user-role'])"
                    command="perfil"
                  >
                    Dar perfil…
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="checkPermi(['system:permission:assign-user-role'])"
                    command="tirar_perfil"
                  >
                    Tirar perfil…
                  </el-dropdown-item>
                  <el-dropdown-item v-if="checkPermi(['identidade:mfa:redefinir'])" command="mfa">
                    Redefinir verificação em duas etapas
                  </el-dropdown-item>
                  <el-dropdown-item v-if="checkPermi(['system:user:update'])" command="encerrar">
                    Encerrar sessões
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="checkPermi(['system:user:delete'])"
                    command="excluir"
                    divided
                  >
                    <span class="text-[var(--el-color-danger)]">Excluir</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button link size="small" @click="tabela?.clearSelection()"
              >Limpar seleção</el-button
            >
          </div>

          <el-table
            ref="tabela"
            v-loading="loading"
            :data="lista"
            :default-sort="{ prop: 'nome', order: 'ascending' }"
            class="cursor-pointer"
            row-key="id"
            @row-click="(r: IdentidadeApi.UsuarioLinha) => fichaRef?.abrir(r.id)"
            @selection-change="(l: IdentidadeApi.UsuarioLinha[]) => (selecionados = l)"
            @sort-change="ordenar"
          >
            <el-table-column type="selection" width="45" />
            <el-table-column label="Nome" min-width="200" prop="nome" sortable="custom">
              <template #default="{ row }">
                <div class="font-medium">{{ row.nickname }}</div>
                <div class="ajuda"
                  >{{ row.username }}<span v-if="row.email"> · {{ row.email }}</span></div
                >
              </template>
            </el-table-column>
            <el-table-column label="Unidade" min-width="160" prop="unidade" sortable="custom">
              <template #default="{ row }">
                <el-tooltip :content="origemUnidade(row)" placement="top">
                  <el-link
                    :type="row.unidadeId ? 'primary' : 'info'"
                    :underline="false"
                    @click.stop="daColuna(row)"
                  >
                    {{ row.unidadeNome || (row.unidadeOrigem === 'fora' ? 'IP fora das faixas' : '—') }}
                  </el-link>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="Perfis" min-width="190">
              <template #default="{ row }">
                <el-tag
                  v-for="p in row.perfis"
                  :key="p.id"
                  :type="p.codigo === 'super_admin' ? 'danger' : 'info'"
                  class="mb-2px mr-4px"
                  disable-transitions
                  size="small"
                  >{{ p.nome }}</el-tag
                >
                <el-tooltip
                  v-if="row.privilegiado"
                  content="Administra usuários, perfis ou permissões"
                  placement="top"
                >
                  <Icon class="align-middle text-[var(--el-color-danger)]" icon="ep:key" />
                </el-tooltip>
                <span v-if="!row.perfis.length" class="ajuda">sem perfil</span>
              </template>
            </el-table-column>
            <el-table-column label="Origem" width="120">
              <template #default="{ row }">
                <el-tag
                  v-if="row.origem === 'ad'"
                  :type="row.adAtivo === false ? 'danger' : 'success'"
                  disable-transitions
                  size="small"
                  >{{ row.adAtivo === false ? 'Desativado no AD' : 'AD' }}</el-tag
                >
                <el-tag v-else disable-transitions size="small" type="info">Local</el-tag>
              </template>
            </el-table-column>
            <el-table-column align="center" label="2 etapas" width="85">
              <template #default="{ row }">
                <Icon
                  v-if="row.mfa"
                  class="text-[var(--el-color-success)]"
                  icon="ep:circle-check-filled"
                />
                <span v-else class="ajuda">—</span>
              </template>
            </el-table-column>
            <el-table-column label="Último acesso" prop="acesso" sortable="custom" width="150">
              <template #default="{ row }">
                <el-tooltip
                  :content="row.ultimoIp ? `IP ${row.ultimoIp}` : 'Nunca entrou'"
                  placement="top"
                >
                  <span :class="{ parado: parado(row) }">{{ quando(row.ultimoAcesso) }}</span>
                </el-tooltip>
                <div v-if="row.sessoes" class="ajuda">{{ row.sessoes }} sessão(ões) aberta(s)</div>
              </template>
            </el-table-column>
            <el-table-column label="Situação" prop="situacao" sortable="custom" width="105">
              <template #default="{ row }">
                <span @click.stop>
                  <el-switch
                    v-model="row.status"
                    :active-value="0"
                    :disabled="!checkPermi(['system:user:update'])"
                    :inactive-value="1"
                    @change="handleStatusChange(row)"
                  />
                </span>
              </template>
            </el-table-column>
            <el-table-column align="center" label="Ações" width="150">
              <template #default="{ row }">
                <div class="flex items-center justify-center" @click.stop>
                  <el-button
                    v-hasPermi="['system:user:update']"
                    link
                    type="primary"
                    @click="openForm('update', row.id)"
                  >
                    <Icon icon="ep:edit" />Editar
                  </el-button>
                  <el-dropdown
                    v-hasPermi="[
                      'system:user:delete',
                      'system:user:update-password',
                      'system:permission:assign-user-role',
                      'identidade:mfa:redefinir'
                    ]"
                    @command="(c: string) => handleCommand(c, row)"
                  >
                    <el-button link type="primary"><Icon icon="ep:d-arrow-right" /> Mais</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item
                          v-if="checkPermi(['system:user:update-password'])"
                          command="senha"
                        >
                          <Icon icon="ep:key" />Redefinir senha
                        </el-dropdown-item>
                        <el-dropdown-item
                          v-if="checkPermi(['system:permission:assign-user-role'])"
                          command="perfis"
                        >
                          <Icon icon="ep:circle-check" />Atribuir perfis
                        </el-dropdown-item>
                        <el-dropdown-item
                          v-if="checkPermi(['identidade:mfa:redefinir'])"
                          command="mfa"
                        >
                          <Icon icon="ep:iphone" />Redefinir verificação em duas etapas
                        </el-dropdown-item>
                        <el-dropdown-item
                          v-if="checkPermi(['system:user:update']) && row.sessoes"
                          command="encerrar"
                        >
                          <Icon icon="ep:switch-button" />Encerrar sessões
                        </el-dropdown-item>
                        <el-dropdown-item
                          v-if="checkPermi(['system:user:delete'])"
                          command="excluir"
                        >
                          <Icon icon="ep:delete" />Excluir
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <Pagination
            v-model:limit="q.pageSize"
            v-model:page="q.pageNo"
            :total="total"
            @pagination="carregar"
          />
        </ContentWrap>
      </template>
  </div>

  <UserForm ref="formRef" @success="carregar" />
  <UserAssignRoleForm ref="assignRoleFormRef" @success="carregar" />
  <FichaUsuario ref="fichaRef" @editar="(id) => openForm('update', id)" @mudou="carregar" />
  <ImportarPlanilha
    ref="importador"
    :analisar="IdentidadeApi.analisarImportacaoUsuarios"
    :aplicar="IdentidadeApi.importarUsuarios"
    desativar
    nome-plural="usuários"
    nome-singular="usuário"
    @importado="carregar"
  />

  <Dialog v-model="escolha.visivel" :title="escolha.titulo" width="460px">
    <el-tree-select
      v-if="escolha.acao === 'mover'"
      v-model="escolha.deptId"
      :data="depts"
      :props="defaultProps"
      check-strictly
      class="!w-full"
      filterable
      node-key="id"
      placeholder="Departamento"
    />
    <el-select v-else v-model="escolha.roleId" class="!w-full" filterable placeholder="Perfil">
      <el-option v-for="r in perfis" :key="r.id" :label="r.name" :value="r.id" />
    </el-select>
    <div class="ajuda mt-8px">
      {{ selecionados.length }} usuário(s). Contas do AD: se o AD espelha as OUs, o departamento
      volta para o da OU na próxima sincronização.
    </div>
    <template #footer>
      <el-button
        :disabled="escolha.acao === 'mover' ? !escolha.deptId : !escolha.roleId"
        type="primary"
        @click="confirmarEscolha"
        >Aplicar</el-button
      >
      <el-button @click="escolha.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { ElMessageBox, type TableInstance } from 'element-plus'
import { checkPermi } from '@/utils/permission'
import download from '@/utils/download'
import { CommonStatusEnum } from '@/utils/constants'
import { defaultProps, handleTree } from '@/utils/tree'
import * as UserApi from '@/api/system/user'
import * as RoleApi from '@/api/system/role'
import * as DeptApi from '@/api/system/dept'
import * as IdentidadeApi from '@/api/identidade'
import ImportarPlanilha from '@/components/ImportarPlanilha/index.vue'
import UserForm from './UserForm.vue'
import UserAssignRoleForm from './UserAssignRoleForm.vue'
import FichaUsuario from './FichaUsuario.vue'
import RevisaoAcessos from './RevisaoAcessos.vue'

defineOptions({ name: 'SystemUser' })

const message = useMessage()
const aba = ref('usuarios')
const loading = ref(true)
const total = ref(0)
const lista = ref<IdentidadeApi.UsuarioLinha[]>([])
const selecionados = ref<IdentidadeApi.UsuarioLinha[]>([])
const perfis = ref<RoleApi.RoleVO[]>([])
const depts = ref<any[]>([])
const tabela = ref<TableInstance>()
const fichaRef = ref<InstanceType<typeof FichaUsuario>>()
const importador = ref<InstanceType<typeof ImportarPlanilha>>()
const exportando = ref(false)

const inicial = () => ({
  pageNo: 1,
  pageSize: 20,
  busca: '',
  status: undefined as number | undefined,
  roleId: undefined as number | undefined,
  origem: undefined as string | undefined,
  semAcessoDias: undefined as number | undefined,
  semMfa: false,
  privilegiado: false,
  unidadeId: undefined as number | undefined,
  ordem: 'nome',
  direcao: 'asc'
})
const q = reactive(inicial())

const quando = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : 'nunca'
const parado = (u: IdentidadeApi.UsuarioLinha) =>
  u.status === 0 &&
  (!u.ultimoAcesso || Date.now() - new Date(u.ultimoAcesso).getTime() > 90 * 86400000)

const params = () => {
  const p: any = { ...q }
  for (const k of Object.keys(p))
    if (p[k] === '' || p[k] === null || p[k] === undefined || p[k] === false) delete p[k]
  return p
}
const carregar = async () => {
  loading.value = true
  try {
    const r = await IdentidadeApi.getUsuarios(params())
    lista.value = r.list
    total.value = r.total
  } finally {
    loading.value = false
  }
}
const buscar = () => {
  q.pageNo = 1
  carregar()
}
const limpar = () => {
  Object.assign(q, inicial())
  buscar()
}
const ordenar = ({ prop, order }: { prop: string; order: 'ascending' | 'descending' | null }) => {
  q.ordem = order ? prop : 'nome'
  q.direcao = order === 'descending' ? 'desc' : 'asc'
  buscar()
}
/** Unidade = a da máquina que a pessoa usa (faixa de IP ou dada à mão na máquina); -1 = sem unidade */
const unidades = ref<any[]>([])
const daColuna = (u: IdentidadeApi.UsuarioLinha) => {
  q.unidadeId = u.unidadeId ?? -1
  buscar()
}
const origemUnidade = (u: IdentidadeApi.UsuarioLinha) => {
  const maq = `${u.unidadeMaquina}${u.unidadeIp ? ' (' + u.unidadeIp + ')' : ''}`
  switch (u.unidadeOrigem) {
    case 'faixa':
      return `Pela faixa de IP da máquina ${maq}. Clique para ver todos desta unidade.`
    case 'manual':
      return `Unidade dada à mão na máquina ${maq}. Clique para ver todos desta unidade.`
    case 'fora':
      return `A máquina ${maq} está num IP fora das faixas cadastradas (Inventário → Rede).`
    default:
      return 'Nenhuma máquina do inventário com esta pessoa.'
  }
}

const formRef = ref()
const openForm = (type: string, id?: number) => formRef.value.open(type, id)
const assignRoleFormRef = ref()

const exportar = async () => {
  exportando.value = true
  try {
    download.excel(await IdentidadeApi.exportarUsuarios(params()), 'usuarios.xlsx')
  } finally {
    exportando.value = false
  }
}

const handleStatusChange = async (row: IdentidadeApi.UsuarioLinha) => {
  try {
    const ativar = row.status === CommonStatusEnum.ENABLE
    await message.confirm(
      `${ativar ? 'Ativar' : 'Desativar'} "${row.username}"?` +
        (ativar ? '' : ' As sessões abertas acabam na hora.') +
        (row.origem === 'ad'
          ? ' A conta vem do AD: a próxima sincronização volta a situação que está lá.'
          : '')
    )
    await UserApi.updateUserStatus(row.id, row.status)
    await carregar()
  } catch {
    row.status =
      row.status === CommonStatusEnum.ENABLE ? CommonStatusEnum.DISABLE : CommonStatusEnum.ENABLE
  }
}

// ---------- ações de uma pessoa ----------
const handleCommand = async (command: string, row: IdentidadeApi.UsuarioLinha) => {
  switch (command) {
    case 'senha':
      return redefinirSenha(row)
    case 'perfis':
      return assignRoleFormRef.value.open({
        id: row.id,
        username: row.username,
        nickname: row.nickname
      } as any)
    case 'mfa':
    case 'encerrar':
      return emLote(command, [row])
    case 'excluir':
      try {
        await message.delConfirm(
          `Excluir "${row.username}"? Prefira desativar: excluir apaga o vínculo com o histórico.`
        )
      } catch {
        return
      }
      await UserApi.deleteUser(row.id)
      message.success('Usuário excluído')
      return carregar()
  }
}

const redefinirSenha = async (row: IdentidadeApi.UsuarioLinha) => {
  if (row.origem === 'ad') {
    message.warning('Esta conta entra com a senha do AD: troque a senha no AD.')
    return
  }
  try {
    const r = await ElMessageBox.prompt(`Nova senha de "${row.username}"`, 'Redefinir senha', {
      confirmButtonText: 'Redefinir',
      cancelButtonText: 'Cancelar',
      inputType: 'password',
      inputValidator: (v) =>
        (v.length >= 8 && v.length <= 16) || 'A senha deve ter de 8 a 16 caracteres'
    })
    await UserApi.resetUserPassword(row.id, r.value)
    message.success('Senha redefinida. Passe-a à pessoa por um canal seguro.')
  } catch {}
}

// ---------- lote ----------
const TEXTOS: Record<string, string> = {
  ativar: 'Ativar',
  desativar: 'Desativar (as sessões abertas acabam na hora)',
  mfa: 'Remover a verificação em duas etapas de',
  encerrar: 'Encerrar as sessões de'
}
const escolha = reactive({
  visivel: false,
  acao: '',
  titulo: '',
  deptId: undefined as number | undefined,
  roleId: undefined as number | undefined
})

const emLote = async (
  acao: string,
  alvos: IdentidadeApi.UsuarioLinha[],
  extra: { deptId?: number; roleId?: number } = {}
) => {
  if (TEXTOS[acao]) {
    try {
      await message.confirm(
        `${TEXTOS[acao]} ${alvos.length === 1 ? `"${alvos[0].username}"` : `${alvos.length} usuários`}?` +
          (acao === 'mfa'
            ? ' Confirme a identidade antes: a pessoa poderá entrar só com a senha.'
            : '')
      )
    } catch {
      return
    }
  }
  const r = await IdentidadeApi.loteUsuarios({ acao, ids: alvos.map((u) => u.id), ...extra })
  if (r.avisos.length) {
    await ElMessageBox.alert(
      r.avisos.join('\n'),
      `${r.feitos} feito(s), ${r.ignorados} ignorado(s)`,
      {
        confirmButtonText: 'Ok'
      }
    )
  } else {
    message.success(`${r.feitos} usuário(s) atualizado(s)`)
  }
  tabela.value?.clearSelection()
  await carregar()
}

const executar = async (acao: string) => {
  const alvos = selecionados.value
  if (acao === 'excluir') {
    try {
      await message.delConfirm(`Excluir ${alvos.length} usuário(s)? Prefira desativar.`)
    } catch {
      return
    }
    await UserApi.deleteUserList(alvos.map((u) => u.id))
    message.success('Usuários excluídos')
    tabela.value?.clearSelection()
    return carregar()
  }
  if (acao === 'mover' || acao === 'perfil' || acao === 'tirar_perfil') {
    if (acao === 'mover' && !depts.value.length) await carregarDepts()
    Object.assign(escolha, {
      visivel: true,
      acao,
      deptId: undefined,
      roleId: undefined,
      titulo:
        acao === 'mover'
          ? 'Mover para o departamento'
          : acao === 'perfil'
            ? 'Dar o perfil'
            : 'Tirar o perfil'
    })
    return
  }
  await emLote(acao, alvos)
}
const confirmarEscolha = async () => {
  escolha.visivel = false
  await emLote(escolha.acao, selecionados.value, { deptId: escolha.deptId, roleId: escolha.roleId })
}

const carregarDepts = async () => {
  depts.value = handleTree(await DeptApi.getSimpleDeptList())
  unidades.value = [
    { id: -1, name: 'Sem unidade (sem máquina ou IP fora das faixas)', children: [] },
    ...depts.value
  ]
}

/** Filtros lembrados neste navegador (só conveniência: sem o storage, a tela abre com o padrão). */
const CHAVE_FILTROS = 'coti.usuarios.filtros'
watch(
  () => ({ ...q, pageNo: 1 }),
  (v) => {
    try {
      localStorage.setItem(CHAVE_FILTROS, JSON.stringify(v))
    } catch {
      /* sem storage */
    }
  },
  { deep: true }
)

onMounted(async () => {
  try {
    const salvo = JSON.parse(localStorage.getItem(CHAVE_FILTROS) || 'null')
    if (salvo && typeof salvo === 'object') {
      const base = inicial()
      for (const k of Object.keys(base)) if (k in salvo && k !== 'pageNo') (q as any)[k] = salvo[k]
    }
  } catch {
    /* sem storage ou valor inválido */
  }
  carregar()
  carregarDepts()
  perfis.value = await RoleApi.getSimpleRoleList()
})
</script>

<style lang="scss" scoped>
.abas :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.ajuda {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.parado {
  color: var(--el-color-warning);
}

.lote {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  margin-bottom: 10px;
  background: var(--el-color-primary-light-9);
  border-radius: 6px;
}
</style>
