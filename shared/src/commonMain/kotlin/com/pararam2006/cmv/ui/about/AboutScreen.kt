package com.pararam2006.cmv.ui.about

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.pararam2006.cmv.ui.Dimens
import custommusicvolume.shared.generated.resources.Res
import custommusicvolume.shared.generated.resources.about_faq_a1
import custommusicvolume.shared.generated.resources.about_faq_a2
import custommusicvolume.shared.generated.resources.about_faq_a3
import custommusicvolume.shared.generated.resources.about_faq_a4
import custommusicvolume.shared.generated.resources.about_faq_a5
import custommusicvolume.shared.generated.resources.about_faq_q1
import custommusicvolume.shared.generated.resources.about_faq_q2
import custommusicvolume.shared.generated.resources.about_faq_q3
import custommusicvolume.shared.generated.resources.about_faq_q4
import custommusicvolume.shared.generated.resources.about_faq_q5
import custommusicvolume.shared.generated.resources.about_faq_title
import custommusicvolume.shared.generated.resources.about_screen_description
import custommusicvolume.shared.generated.resources.app_name
import custommusicvolume.shared.generated.resources.outline_arrow_forward_24
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutScreen(
    version: String,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.screenLayout(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(Dimens.paddingSmall))

        Text(
            text = "v$version",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Dimens.paddingMedium))

        InfoCard {
            Text(
                text = stringResource(Res.string.about_screen_description),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(Dimens.paddingLarge))

        Text(
            text = stringResource(Res.string.about_faq_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(Dimens.paddingLarge))

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.paddingTiny)) {
            FaqItem(
                question = stringResource(Res.string.about_faq_q1),
                answer = stringResource(Res.string.about_faq_a1)
            )

            FaqItem(
                question = stringResource(Res.string.about_faq_q2),
                answer = stringResource(Res.string.about_faq_a2)
            )

            FaqItem(
                question = stringResource(Res.string.about_faq_q3),
                answer = stringResource(Res.string.about_faq_a3)
            )

            FaqItem(
                question = stringResource(Res.string.about_faq_q4),
                answer = stringResource(Res.string.about_faq_a4)
            )

            FaqItem(
                question = stringResource(Res.string.about_faq_q5),
                answer = stringResource(Res.string.about_faq_a5)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.paddingLarge))
    }
}

@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(Dimens.paddingMedium),
            content = content
        )
    }
}

@Composable
fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f, label = "rotation"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.paddingSmall)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    painter = painterResource(Res.drawable.outline_arrow_forward_24),
                    contentDescription = null,
                    modifier = Modifier.rotate(rotationState + 90f),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Modifier.screenLayout(scrollState: ScrollState): Modifier = this
    .fillMaxSize()
    .verticalScroll(scrollState)
    .padding(Dimens.paddingMedium)
