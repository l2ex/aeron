/*
 * Copyright 2014-2017 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.samples;

import io.aeron.Aeron;
import io.aeron.driver.status.StatusUtil;
import org.agrona.concurrent.status.CountersReader;
import org.agrona.concurrent.status.StatusIndicator;

public class SetControllableIdleStrategy
{
    public static void main(final String[] args) throws Exception
    {
        try (Aeron aeron = Aeron.connect())
        {
            final CountersReader countersReader = aeron.countersReader();
            final StatusIndicator statusIndicator = StatusUtil.controllableIdleStrategy(countersReader);

            if (null != statusIndicator)
            {
                final int status = Integer.parseInt(args[0]);

                statusIndicator.setOrdered(status);

                System.out.println("Set ControllableIdleStrategy status to " + status);
            }
            else
            {
                System.out.println("Could not find ControllableIdleStrategy status.");
            }
        }
    }
}
