<template>
  <ContentWrap>
    <el-button v-hasPermi="['itsm:categoria:create']" plain type="primary" @click="abrir()">
      <Icon class="mr-5px" icon="ep:plus" /> Nova categoria
    </el-button>
    <el-button @click="expandir = !expandir"
      ><Icon class="mr-5px" icon="ep:sort" /> Expandir/recolher</el-button
    >
  </ContentWrap>

  <ContentWrap>
    <el-table
      v-if="mostrar"
      v-loading="loading"
      :data="arvore"
      :default-expand-all="expandir"
      row-key="id"
    >
      <el-table-column label="Categoria" min-width="260" prop="nome" />
      <el-table-column label="Código" prop="codigo" width="140" />
      <el-table-column label="Atribui para" min-width="180">
        <template #default="{ row }">{{ responsavel(row) }}</template>
      </el-table-column>
      <el-table-column label="SLA de solução" min-width="160">
        <template #default="{ row }">{{
          slas.find((s) => s.id === row.slaSolucaoId)?.nome || '—'
        }}</template>
      </el-table-column>
      <el-table-column label="Uso" width="200">
        <template #default="{ row }">
          <el-tag v-if="row.paraIncidente" class="mr-4px" disable-transitions size="small"
            >Incidente</el-tag
          >
          <el-tag v-if="row.paraRequisicao" disable-transitions size="small" type="success"
            >Requisição</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column align="center" label="No portal" width="100">
        <template #default="{ row }">{{ row.visivelPortal ? 'Sim' : 'Não' }}</template>
      </el-table-column>
      <el-table-column align="center" label="Ativa" width="80">
        <template #default="{ row }">
          <el-tag :type="row.ativo ? 'success' : 'info'" disable-transitions size="small">{{
            row.ativo ? 'Sim' : 'Não'
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="Ações" width="220">
        <template #default="{ row }">
          <el-button v-hasPermi="['itsm:categoria:update']" link type="primary" @click="abrir(row)"
            >Editar</el-button
          >
          <el-button
            v-hasPermi="['itsm:categoria:create']"
            link
            type="primary"
            @click="abrir(undefined, row.id)"
          >
            Subcategoria
          </el-button>
          <el-button v-hasPermi="['itsm:categoria:delete']" link type="danger" @click="excluir(row)"
            >Excluir</el-button
          >
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <Dialog v-model="dialogo" :title="form.id ? 'Editar categoria' : 'Nova categoria'" width="620px">
    <el-form ref="formRef" :model="form" :rules="regras" label-width="130px">
      <el-form-item label="Categoria superior">
        <el-tree-select
          v-model="form.parentId"
          :data="[{ id: 0, nome: '(raiz)', children: arvore }]"
          :props="{ label: 'nome', children: 'children' }"
          check-strictly
          class="w-full"
          default-expand-all
          node-key="id"
        />
      </el-form-item>
      <el-form-item label="Nome" prop="nome"
        ><el-input v-model="form.nome" maxlength="100"
      /></el-form-item>
      <el-form-item label="Código" prop="codigo">
        <el-input
          v-model="form.codigo"
          maxlength="64"
          placeholder="Opcional; usado por integrações e formulários"
        />
      </el-form-item>
      <el-form-item label="Descrição"
        ><el-input v-model="form.descricao" maxlength="1000" type="textarea"
      /></el-form-item>
      <el-form-item label="Atribuir à equipe">
        <el-select
          v-model="form.equipeId"
          class="w-full"
          clearable
          filterable
          placeholder="Ninguém"
        >
          <el-option v-for="e in equipes" :key="e.id" :label="e.nome" :value="e.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="Atribuir ao técnico">
        <UsuarioSelect
          v-model="form.tecnicoId"
          class="w-full"
          clearable
          filterable
          placeholder="Ninguém"
        />
      </el-form-item>
      <el-form-item label="SLA de atendimento">
        <el-select
          v-model="form.slaAtendimentoId"
          class="w-full"
          clearable
          placeholder="O da prioridade"
        >
          <el-option
            v-for="s in slas.filter((x) => x.tipo === 1)"
            :key="s.id"
            :label="s.nome"
            :value="s.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="SLA de solução">
        <el-select
          v-model="form.slaSolucaoId"
          class="w-full"
          clearable
          placeholder="O da prioridade"
        >
          <el-option
            v-for="s in slas.filter((x) => x.tipo === 0)"
            :key="s.id"
            :label="s.nome"
            :value="s.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="Uso">
        <el-checkbox v-model="form.paraIncidente">Incidente</el-checkbox>
        <el-checkbox v-model="form.paraRequisicao">Requisição</el-checkbox>
        <el-checkbox v-model="form.visivelPortal">Aparece no portal</el-checkbox>
        <el-checkbox v-model="form.ativo">Ativa</el-checkbox>
      </el-form-item>
      <el-form-item label="Ordem"><el-input-number v-model="form.ordem" :min="0" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="dialogo = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import UsuarioSelect from '@/components/UsuarioSelect/index.vue'
import { garantirUsuarios, nomeUsuario } from '@/hooks/web/useUsuarios'
import { handleTree } from '@/utils/tree'
import * as ItsmApi from '@/api/itsm'

defineOptions({ name: 'ItsmCategoria' })
const message = useMessage()

const loading = ref(true)
const lista = ref<ItsmApi.Categoria[]>([])
const arvore = ref<any[]>([])
const equipes = ref<ItsmApi.Equipe[]>([])
const slas = ref<ItsmApi.Sla[]>([])
const expandir = ref(true)
const mostrar = ref(true)
const dialogo = ref(false)
const formRef = ref()
const vazio = () => ({
  id: undefined as number | undefined,
  parentId: 0,
  nome: '',
  codigo: '',
  descricao: '',
  equipeId: undefined as number | undefined,
  tecnicoId: undefined as number | undefined,
  slaAtendimentoId: undefined as number | undefined,
  slaSolucaoId: undefined as number | undefined,
  paraIncidente: true,
  paraRequisicao: true,
  visivelPortal: true,
  ativo: true,
  ordem: 0
})
const form = ref(vazio())
const regras = {
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  codigo: [
    {
      pattern: /^[A-Za-z0-9._-]*$/,
      message: 'Só letras, números, ponto, hífen e sublinhado',
      trigger: 'blur'
    }
  ]
}

watch(expandir, async () => {
  mostrar.value = false
  await nextTick()
  mostrar.value = true
})

const responsavel = (c: ItsmApi.Categoria) => {
  const partes: string[] = []
  if (c.equipeId)
    partes.push(equipes.value.find((e) => e.id === c.equipeId)?.nome || '#' + c.equipeId)
  if (c.tecnicoId) partes.push(nomeUsuario(c.tecnicoId))
  return partes.join(' · ') || '—'
}

const carregar = async () => {
  loading.value = true
  try {
    lista.value = await ItsmApi.getCategorias()
    garantirUsuarios(lista.value.map((c) => c.tecnicoId))
    arvore.value = handleTree(
      [...lista.value].sort((a, b) => a.ordem - b.ordem || a.nome.localeCompare(b.nome)),
      'id',
      'parentId'
    )
  } finally {
    loading.value = false
  }
}

const abrir = (c?: ItsmApi.Categoria, parentId?: number) => {
  form.value = c
    ? { ...vazio(), ...c, codigo: c.codigo || '' }
    : { ...vazio(), parentId: parentId ?? 0 }
  dialogo.value = true
}

const salvar = async () => {
  if (!(await formRef.value.validate().catch(() => false))) return
  await ItsmApi.salvarCategoria(form.value)
  message.success('Categoria salva')
  dialogo.value = false
  await carregar()
}

const excluir = async (c: ItsmApi.Categoria) => {
  await message.delConfirm(`Excluir a categoria "${c.nomeCompleto}"?`)
  await ItsmApi.excluirCategoria(c.id)
  message.success('Excluída')
  await carregar()
}

onMounted(async () => {
  ;[equipes.value, slas.value] = await Promise.all([
    ItsmApi.getEquipes(),
    ItsmApi.getSlas().catch(() => [])
  ])
  await carregar()
})
</script>
