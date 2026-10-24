<template>
  <Dialog v-model="visivel" :title="ehMudanca ? 'Nova mudança' : 'Novo problema'" width="760px">
    <el-form ref="formRef" :model="form" :rules="regras" label-width="110px">
      <el-form-item label="Título" prop="titulo">
        <el-input v-model="form.titulo" maxlength="255" show-word-limit />
      </el-form-item>
      <el-form-item v-if="ehMudanca" label="Tipo">
        <el-radio-group v-model="form.tipo">
          <el-radio-button v-for="t in TIPOS_MUDANCA" :key="t.valor" :value="t.valor">{{
            t.rotulo
          }}</el-radio-button>
        </el-radio-group>
        <div class="w-full text-12px text-[var(--el-text-color-secondary)]">
          {{ TIPOS_MUDANCA.find((t) => t.valor === form.tipo)?.dica }}
        </div>
      </el-form-item>
      <el-form-item label="Categoria">
        <el-select v-model="form.categoriaId" class="w-full" clearable filterable>
          <el-option v-for="c in categorias" :key="c.id" :label="c.nomeCompleto" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-row>
        <el-col :span="12">
          <el-form-item label="Urgência">
            <el-select v-model="form.urgencia">
              <el-option v-for="e in ESCALA" :key="e.valor" :label="e.rotulo" :value="e.valor" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Impacto">
            <el-select v-model="form.impacto">
              <el-option v-for="e in ESCALA" :key="e.valor" :label="e.rotulo" :value="e.valor" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item v-if="ehMudanca" label="Janela">
        <el-date-picker
          v-model="form.janela"
          end-placeholder="Fim"
          start-placeholder="Início previsto"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item label="Técnicos">
        <el-select
          v-model="form.tecnicoIds"
          class="w-full"
          filterable
          multiple
          placeholder="Ninguém ainda"
        >
          <el-option v-for="u in usuarios" :key="u.id" :label="u.nickname" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="Descrição">
        <Editor v-model="form.descricao" :directory="objeto" height="200px" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :loading="enviando" type="primary" @click="salvar">Registrar</el-button>
      <el-button @click="visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ItsmApi from '@/api/itsm'
import { ESCALA, TIPOS_MUDANCA, type ObjetoItil } from '@/api/itsm'
import * as UserApi from '@/api/system/user'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'ItilForm' })
const props = defineProps<{ objeto: ObjetoItil }>()
const emit = defineEmits<{ success: [id: number] }>()
const ehMudanca = computed(() => props.objeto === 'mudanca')

const visivel = ref(false)
const enviando = ref(false)
const formRef = ref()
const categorias = ref<ItsmApi.Categoria[]>([])
const usuarios = ref<UserApi.UserVO[]>([])
const vazio = () => ({
  titulo: '',
  tipo: 2,
  categoriaId: undefined as number | undefined,
  urgencia: 3,
  impacto: 3,
  janela: [] as string[],
  tecnicoIds: [useUserStore().getUser.id] as number[],
  descricao: ''
})
const form = ref(vazio())
const regras = { titulo: [{ required: true, message: 'Informe o título', trigger: 'blur' }] }

const open = async () => {
  form.value = vazio()
  visivel.value = true
  ;[categorias.value, usuarios.value] = await Promise.all([
    ItsmApi.getCategorias(true),
    UserApi.getSimpleUserList()
  ])
}
defineExpose({ open })

const salvar = async () => {
  await formRef.value.validate()
  enviando.value = true
  try {
    const f = form.value
    const id = await ItsmApi.criarItil(props.objeto, {
      titulo: f.titulo,
      tipo: ehMudanca.value ? f.tipo : undefined,
      categoriaId: f.categoriaId,
      urgencia: f.urgencia,
      impacto: f.impacto,
      inicioPrevisto: ehMudanca.value && f.janela?.length ? f.janela[0] : undefined,
      fimPrevisto: ehMudanca.value && f.janela?.length ? f.janela[1] : undefined,
      tecnicoIds: f.tecnicoIds,
      descricao: f.descricao
    })
    visivel.value = false
    emit('success', id)
  } finally {
    enviando.value = false
  }
}
</script>
