/* package net.guardapanda.optimization;

import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "guardapanda")
public class Memory {

    // Flag para garantir que a limpeza ocorra apenas uma vez por intervalo de tempo
    private static long lastCleanTime = 0;

    // Intervalo de tempo em milissegundos (1 segundo = 1000 milissegundos)
    private static final long CLEAN_INTERVAL = 1000;

    // Método chamado a cada tick
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            long currentTime = System.currentTimeMillis();

            // Verifica se já se passou o intervalo desejado
            if (currentTime - lastCleanTime >= CLEAN_INTERVAL) {
                lastCleanTime = currentTime;

                // Chama a coleta de lixo
                System.gc(); // Limpeza de memória (coleta de lixo)
            }
        }
    }
}
*/
