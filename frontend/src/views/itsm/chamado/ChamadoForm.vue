<template>
  <Dialog v-model="visivel" title="Novo chamado" width="900px">
    <el-form ref="formRef" v-loading="carregando" :model="form" :rules="regras" label-width="110px">
      <el-form-item label="Título" prop="titulo">
        <el-input
          v-model="form.titulo"
          maxlength="255"
          placeholder="Resumo do problema ou do pedido"
          show-word-limit
        />
      </el-form-item>
      <el-row>
        <el-col :span="12">
          <el-form-item label="Tipo" prop="tipo">
            <el-radio-group v-model="form.tipo">
              <el-radio-button :value="1">Incidente</el-radio-button>
              <el-radio-button :value="2">Requisição</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Categoria" prop="categoriaId">
            <el-select
              v-model="form.categoriaId"
              class="w-full"
              clearable
              filterable
              placeholder="Selecione"
            >
              <el-option
                v-for="c in categorias"
                :key="c.id"
                :label="c.nomeCompleto"
                :value="c.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="Urgência" prop="urgencia">
            <el-select v-model="form.urgencia" class="w-full">
              <el-option v-for="e in ESCALA" :key="e.valor" :label="e.rotulo" :value="e.valor" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="Impacto" prop="impacto">
            <el-select v-model="form.impacto" class="w-full">
              <el-option v-for="e in ESCALA" :key="e.valor" :label="e.rotulo" :value="e.valor" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="Prioridade">
            <el-select v-model="form.prioridade" class="w-full" clearable placeholder="Automática">
              <el-option
                v-for="p in PRIORIDADES"
                :key="p.valor"
                :label="p.rotulo"
                :value="p.valor"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Solicitantes">
            <UsuarioSelect
              v-model="form.solicitanteIds"
              class="w-full"
              filterable
              multiple
              placeholder="Eu mesmo"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Origem">
            <el-select v-model="form.origemId" class="w-full" clearable placeholder="Portal">
              <el-option v-for="o in origens" :key="o.id" :label="o.nome" :value="o.id!" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Técnicos">
            <UsuarioSelect
              v-model="form.tecnicoIds"
              class="w-full"
              filterable
              multiple
              placeholder="Pela categoria"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Equipes">
            <el-select
              v-model="form.equipeIds"
              class="w-full"
              filterable
              multiple
              placeholder="Pela categoria"
            >
              <el-option v-for="e in equipes" :key="e.id" :label="e.nome" :value="e.id!" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="Descrição">
        <Editor v-model="form.descricao" directory="chamados" height="260px" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="carregando" type="primary" @click="salvar">Abrir chamado</el-button>
      <el-button @click="visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import UsuarioSelect from '@/components/UsuarioSelect/index.vue'
import * as ItsmApi from '@/api/itsm'
import { ESCALA, PRIORIDADES } from '@/api/itsm'

defineOptions({ name: 'ChamadoForm' })
const emit = defineEmits(['success'])
const message = useMessage()

const visivel = ref(false)
const carregando = ref(false)
const formRef = ref()
const categorias = ref<ItsmApi.Categoria[]>([])
const equipes = ref<ItsmApi.Equipe[]>([])
const origens = ref<ItsmApi.Origem[]>([])

const vazio = () => ({
  titulo: '',
  tipo: 1,
  urgencia: 3,
  impacto: 3,
  prioridade: undefined as number | undefined,
  categoriaId: undefined as number | undefined,
  origemId: undefined as number | undefined,
  solicitanteIds: [] as number[],
  tecnicoIds: [] as number[],
  equipeIds: [] as number[],
  descricao: ''
})
const form = ref(vazio())
const regras = { titulo: [{ required: true, message: 'Informe o título', trigger: 'blur' }] }

const open = async () => {
  visivel.value = true
  form.value = vazio()
  carregando.value = true
  try {
    ;[categorias.value, equipes.value, origens.value] = await Promise.all([
      ItsmApi.getCategorias(true),
      ItsmApi.getEquipes(true),
      ItsmApi.getOrigens(true)
    ])
  } finally {
    carregando.value = false
  }
}
defineExpose({ open })

const salvar = async () => {
  if (!(await formRef.value.validate().catch(() => false))) return
  carregando.value = true
  try {
    const id = await ItsmApi.criarChamado(form.value)
    message.success(`Chamado #${id} aberto`)
    visivel.value = false
    emit('success', id)
  } finally {
    carregando.value = false
  }
}
</script>
