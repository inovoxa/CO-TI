<template>
  <ContentWrap>
    <el-input v-model="busca" class="!w-320px" clearable placeholder="Procurar link">
      <template #prefix><Icon icon="ep:search" /></template>
    </el-input>
  </ContentWrap>

  <div v-loading="loading">
    <ContentWrap>
      <div class="titulo">
        <span>Meus links ({{ painel.meus.length }}/{{ painel.limiteMeus }})</span>
        <el-button
          :disabled="painel.meus.length >= painel.limiteMeus"
          plain
          size="small"
          type="primary"
          @click="abrir('meu')"
        >
          <Icon class="mr-4px" icon="ep:plus" /> Adicionar
        </el-button>
      </div>
      <div v-if="painel.meus.length >= painel.limiteMeus" class="dica">
        Você atingiu o limite de {{ painel.limiteMeus }} links. Remova um para adicionar outro.
      </div>
      <el-empty
        v-if="!meusVisiveis.length"
        :description="busca ? 'Nenhum link seu com esse nome' : 'Adicione os sistemas e sites que você mais usa. Só você vê estes links.'"
        :image-size="60"
      />
      <div class="grade">
        <LinkCartao
          v-for="l in meusVisiveis"
          :key="l.id"
          :link="l"
          editavel
          @editar="abrir('meu', l)"
          @excluir="excluir('meu', l)"
        />
      </div>
    </ContentWrap>

    <ContentWrap>
      <div class="titulo">
        <span>Links do órgão</span>
        <el-button v-if="podeGerenciar" plain size="small" type="primary" @click="abrir('publico')">
          <Icon class="mr-4px" icon="ep:plus" /> Adicionar
        </el-button>
      </div>
      <el-empty v-if="!grupos.length" :description="busca ? 'Nenhum link com esse nome' : 'Nenhum link cadastrado ainda'" :image-size="60" />
      <template v-for="g in grupos" :key="g.nome">
        <div v-if="g.nome" class="grupo">{{ g.nome }}</div>
        <div class="grade">
          <LinkCartao
            v-for="l in g.links"
            :key="l.id"
            :editavel="podeGerenciar"
            :link="l"
            @editar="abrir('publico', l)"
            @excluir="excluir('publico', l)"
          />
        </div>
      </template>
    </ContentWrap>
  </div>

  <Dialog v-model="form.visivel" :title="tituloDialogo" width="480px">
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-position="top">
      <el-form-item label="Nome" prop="titulo">
        <el-input v-model="form.dados.titulo" maxlength="100" placeholder="Ex.: Portal da Transparência" />
      </el-form-item>
      <el-form-item label="Endereço" prop="url">
        <el-input v-model="form.dados.url" maxlength="1000" placeholder="https://..." />
      </el-form-item>
      <el-form-item label="Descrição (opcional)">
        <el-input v-model="form.dados.descricao" maxlength="200" />
      </el-form-item>
      <template v-if="form.tipo === 'publico'">
        <el-form-item label="Categoria (opcional)">
          <el-select v-model="form.dados.categoria" allow-create class="w-full" clearable default-first-option filterable placeholder="Ex.: Sistemas, Sites institucionais">
            <el-option v-for="c in categorias" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
      </template>
      <el-form-item label="Ordem">
        <el-input-number v-model="form.dados.ordem" :max="9999" :min="0" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :loading="salvando" type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import type { FormRules } from 'element-plus'
import * as Api from '@/api/acesso'
import { checkPermi } from '@/utils/permission'
import LinkCartao from './LinkCartao.vue'

/** Links públicos, do administrador, e pessoais, que só a própria pessoa vê. */
defineOptions({ name: 'AcessoRapido' })
const message = useMessage()
const podeGerenciar = checkPermi(['acesso:link:gerenciar'])

const loading = ref(true)
const salvando = ref(false)
const busca = ref('')
const painel = ref<Api.Painel>({ publicos: [], meus: [], limiteMeus: 10 })

const casa = (l: Api.Link) => {
  const b = busca.value.trim().toLowerCase()
  return !b || [l.titulo, l.descricao, l.categoria, l.url].some((x) => x?.toLowerCase().includes(b))
}
const meusVisiveis = computed(() => painel.value.meus.filter(casa))
const categorias = computed(() => [...new Set(painel.value.publicos.map((l) => l.categoria).filter(Boolean))] as string[])
const grupos = computed(() => {
  const m = new Map<string, Api.Link[]>()
  painel.value.publicos.filter(casa).forEach((l) => {
    const c = l.categoria || ''
    m.set(c, [...(m.get(c) || []), l])
  })
  // os sem categoria ficam por último
  return [...m.entries()]
    .sort(([a], [b]) => (a ? (b ? a.localeCompare(b) : -1) : 1))
    .map(([nome, links]) => ({ nome, links }))
})

const carregar = async () => {
  try {
    painel.value = await Api.getPainel()
  } finally {
    loading.value = false
  }
}

const formRef = ref()
const form = reactive<{ visivel: boolean; tipo: 'meu' | 'publico'; dados: Api.Link }>({
  visivel: false,
  tipo: 'meu',
  dados: { titulo: '', url: '', ordem: 0 }
})
const regras: FormRules = {
  titulo: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  url: [{ required: true, message: 'Informe o endereço', trigger: 'blur' }]
}
const tituloDialogo = computed(() =>
  `${form.dados.id ? 'Editar' : 'Novo'} link ${form.tipo === 'publico' ? 'do órgão' : 'pessoal'}`
)
const abrir = (tipo: 'meu' | 'publico', l?: Api.Link) => {
  form.tipo = tipo
  form.dados = l ? { ...l } : { titulo: '', url: '', ordem: 0 }
  form.visivel = true
  nextTick(() => formRef.value?.clearValidate())
}
const salvar = async () => {
  if (!(await formRef.value.validate().catch(() => false))) return
  salvando.value = true
  try {
    await (form.tipo === 'publico' ? Api.salvarPublico : Api.salvarMeu)(form.dados)
    form.visivel = false
    message.success('Link salvo')
    await carregar()
  } finally {
    salvando.value = false
  }
}
const excluir = async (tipo: 'meu' | 'publico', l: Api.Link) => {
  await message.confirm(`Excluir o link "${l.titulo}"?`)
  await (tipo === 'publico' ? Api.excluirPublico : Api.excluirMeu)(l.id!)
  message.success('Link excluído')
  await carregar()
}

onMounted(carregar)
</script>

<style scoped>
.titulo {
  display: flex;
  margin-bottom: 10px;
  font-weight: 600;
  align-items: center;
  justify-content: space-between;
}

.dica {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-color-warning);
}

.grupo {
  margin: 12px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.grade {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
}
</style>
