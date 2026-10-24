<template>
  <el-row :gutter="12">
    <el-col :lg="10" :span="24">
      <ContentWrap title="Etiquetas">
        <el-button class="mb-10px" plain type="primary" @click="abrirEtiqueta()"
          ><Icon class="mr-4px" icon="ep:plus" />Nova etiqueta</el-button
        >
        <el-table :data="etiquetas" size="small">
          <el-table-column label="Etiqueta">
            <template #default="{ row }"
              ><span class="cor" :style="{ background: row.cor }"></span>{{ row.nome }}</template
            >
          </el-table-column>
          <el-table-column label="Descrição" prop="descricao" show-overflow-tooltip />
          <el-table-column width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click="abrirEtiqueta(row)"
                ><Icon icon="ep:edit"
              /></el-button>
              <el-button link type="danger" @click="excluirEtiqueta(row)"
                ><Icon icon="ep:delete"
              /></el-button>
            </template>
          </el-table-column>
        </el-table>
      </ContentWrap>
    </el-col>
    <el-col :lg="14" :span="24">
      <ContentWrap title="Respostas prontas">
        <el-button class="mb-10px" plain type="primary" @click="abrirResposta()"
          ><Icon class="mr-4px" icon="ep:plus" />Nova resposta</el-button
        >
        <el-table :data="respostas" size="small">
          <el-table-column label="Atalho" width="150"
            ><template #default="{ row }">/{{ row.atalho }}</template></el-table-column
          >
          <el-table-column label="Texto" prop="conteudo" show-overflow-tooltip />
          <el-table-column width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click="abrirResposta(row)"
                ><Icon icon="ep:edit"
              /></el-button>
              <el-button link type="danger" @click="excluirResposta(row)"
                ><Icon icon="ep:delete"
              /></el-button>
            </template>
          </el-table-column>
        </el-table>
      </ContentWrap>
    </el-col>
  </el-row>

  <Dialog
    v-model="et.visivel"
    :title="et.dados.id ? 'Editar etiqueta' : 'Nova etiqueta'"
    width="420px"
  >
    <el-form label-width="90px">
      <el-form-item label="Nome"><el-input v-model="et.dados.nome" maxlength="50" /></el-form-item>
      <el-form-item label="Cor"><el-color-picker v-model="et.dados.cor" /></el-form-item>
      <el-form-item label="Descrição"
        ><el-input v-model="et.dados.descricao" maxlength="255"
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvarEtiqueta">Salvar</el-button>
      <el-button @click="et.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>

  <Dialog
    v-model="rp.visivel"
    :title="rp.dados.id ? 'Editar resposta' : 'Nova resposta'"
    width="560px"
  >
    <el-form label-width="90px">
      <el-form-item label="Atalho">
        <el-input v-model="rp.dados.atalho" maxlength="50" placeholder="horario"
          ><template #prepend>/</template></el-input
        >
      </el-form-item>
      <el-form-item label="Texto">
        <el-input
          v-model="rp.dados.conteudo"
          :rows="6"
          maxlength="4096"
          show-word-limit
          type="textarea"
        />
        <div class="text-12px text-[var(--el-text-color-secondary)]"
          >Formatação do WhatsApp: *negrito*, _itálico_, ~riscado~.</div
        >
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvarResposta">Salvar</el-button>
      <el-button @click="rp.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Atd from '@/api/atendimento'

defineOptions({ name: 'AtendimentoEtiqueta' })
const message = useMessage()

const etiquetas = ref<Atd.Etiqueta[]>([])
const respostas = ref<Atd.RespostaPronta[]>([])
const et = reactive({ visivel: false, dados: {} as Atd.Etiqueta })
const rp = reactive({ visivel: false, dados: {} as Atd.RespostaPronta })

const carregar = async () => {
  ;[etiquetas.value, respostas.value] = await Promise.all([Atd.getEtiquetas(), Atd.getRespostas()])
}

const abrirEtiqueta = (e?: Atd.Etiqueta) => {
  et.dados = e ? { ...e } : { nome: '', cor: '#2a78d6' }
  et.visivel = true
}
const salvarEtiqueta = async () => {
  await Atd.salvarEtiqueta(et.dados)
  et.visivel = false
  await carregar()
}
const excluirEtiqueta = async (e: Atd.Etiqueta) => {
  await message.delConfirm(`Excluir a etiqueta ${e.nome}? Ela sai de todas as conversas.`)
  await Atd.excluirEtiqueta(e.id!)
  await carregar()
}
const abrirResposta = (r?: Atd.RespostaPronta) => {
  rp.dados = r ? { ...r } : { atalho: '', conteudo: '' }
  rp.visivel = true
}
const salvarResposta = async () => {
  await Atd.salvarResposta(rp.dados)
  rp.visivel = false
  await carregar()
}
const excluirResposta = async (r: Atd.RespostaPronta) => {
  await message.delConfirm(`Excluir a resposta /${r.atalho}?`)
  await Atd.excluirResposta(r.id!)
  await carregar()
}

onMounted(carregar)
</script>

<style scoped>
.cor {
  display: inline-block;
  width: 10px;
  height: 10px;
  margin-right: 6px;
  border-radius: 50%;
}
</style>
